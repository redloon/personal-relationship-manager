"""Binary Android captures and generated-path artifact publication.

The caller owns emulator identity verification, command deadlines and the shared
runner lock. ``device(*args)`` returns raw stdout bytes and raises on failure.
"""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import uuid

MAX_BYTES = 128 * 1024 * 1024
PNG_HEADER = b'\x89PNG\r\n\x1a\n'


def _identifier(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{32}', value):
        raise ValueError('Invalid artifact/session identifier')
    return value


def _directory(path):
    """Create/open a directory without following symlinks in any component."""
    path = Path(os.path.abspath(path))
    fd = os.open(path.anchor, os.O_RDONLY | os.O_DIRECTORY)
    try:
        for component in path.parts[1:]:
            try:
                os.mkdir(component, 0o700, dir_fd=fd)
            except FileExistsError:
                pass
            child = os.open(component, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = child
        return fd
    except BaseException:
        os.close(fd)
        raise


def _read(directory, name):
    fd = _directory(directory)
    try:
        file_fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
        with os.fdopen(file_fd, 'rb') as source:
            info = os.fstat(source.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_BYTES:
                raise ValueError('Invalid artifact file size/type')
            return source.read(MAX_BYTES + 1)
    finally:
        os.close(fd)


def _write(directory, name, data):
    fd = _directory(directory)
    temporary = '.' + uuid.uuid4().hex + '.tmp'
    try:
        try:
            if stat.S_ISLNK(os.stat(name, dir_fd=fd, follow_symlinks=False).st_mode):
                raise ValueError('Refusing artifact symlink')
        except FileNotFoundError:
            pass
        file_fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=fd)
        with os.fdopen(file_fd, 'wb') as destination:
            destination.write(data)
            destination.flush()
            os.fsync(destination.fileno())
        os.replace(temporary, name, src_dir_fd=fd, dst_dir_fd=fd)
    finally:
        try:
            os.unlink(temporary, dir_fd=fd)
        except FileNotFoundError:
            pass
        os.close(fd)


class Captures:
    def __init__(self, directory, outbox, owner_id, app_sha256, test_sha256, device):
        self.owner_id = _identifier(owner_id)
        for value in [app_sha256, test_sha256]:
            if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value):
                raise ValueError('Invalid APK hash')
        self.directory = Path(os.path.abspath(directory))
        self.outbox = Path(os.path.abspath(outbox))
        self.app_sha256 = app_sha256
        self.test_sha256 = test_sha256
        self.device = device
        self.recording = None
        self.errors = []
        self._artifacts = []
        os.close(_directory(self.directory))
        try:
            manifest = json.loads(_read(self.directory, 'artifacts.json'))
        except FileNotFoundError:
            pass
        else:
            if manifest.get('owner_id') != self.owner_id:
                raise ValueError('Artifact manifest owner mismatch')
            self._artifacts = manifest['artifacts']

    def _save(self):
        data = json.dumps(dict(owner_id=self.owner_id, artifacts=self._artifacts), indent=2).encode() + b'\n'
        _write(self.directory, 'artifacts.json', data)
        _write(self.outbox/self.owner_id, 'manifest.json', data)

    def _publish(self, data, mime, suffix, label):
        if not isinstance(data, bytes) or not 0 < len(data) <= MAX_BYTES:
            raise ValueError('Invalid capture size')
        artifact_id = uuid.uuid4().hex
        name = artifact_id + suffix
        _write(self.directory, name, data)
        _write(self.outbox/self.owner_id, name, data)
        artifact = dict(artifact_id=artifact_id, owner_id=self.owner_id,
                        app_sha256=self.app_sha256, test_sha256=self.test_sha256,
                        captured_at=datetime.now(timezone.utc).isoformat(),
                        mime_type=mime, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                        label=label, relative_path=f'{self.owner_id}/{name}',
                        shared_path=str(self.outbox/self.owner_id/name))
        self._artifacts.append(artifact)
        self._save()
        return dict(artifact)

    def screenshot(self, label='screen'):
        if not isinstance(label, str) or len(label) > 200:
            raise ValueError('Screenshot label must be at most 200 characters')
        data = self.device('exec-out', 'screencap', '-p')
        if not data.startswith(PNG_HEADER):
            raise ValueError('Device did not return a PNG screenshot')
        return self._publish(data, 'image/png', '.png', label)

    def list_artifacts(self):
        return [dict(item) for item in self._artifacts]

    def artifact_file(self, artifact_id):
        _identifier(artifact_id)
        artifact = next((item for item in self._artifacts if item['artifact_id'] == artifact_id), None)
        if artifact is None:
            raise ValueError('Unknown artifact')
        suffix = {'image/png': '.png', 'video/mp4': '.mp4'}.get(artifact['mime_type'])
        if suffix is None:
            raise ValueError('Unknown artifact type')
        name = artifact_id + suffix
        data = _read(self.directory, name)
        if hashlib.sha256(data).hexdigest() != artifact['sha256']:
            raise ValueError('Artifact hash mismatch')
        return self.directory/name

    def start_recording(self):
        if self.recording is not None:
            raise ValueError('A recording is already active')
        recording_id = uuid.uuid4().hex
        path = f'/sdcard/android-qa-{self.owner_id}-{recording_id}.mp4'
        # Every interpolated token below is generated locally or numeric. The
        # recorder closes all inherited streams so adb shell can return.
        script = (
            f'screenrecord --time-limit 120 {path} </dev/null >/dev/null 2>&1 & '
            'pid=$!; n=0; '
            'while [ "$n" -lt 30 ]; do '
            'if [ "$(cat /proc/$pid/comm 2>/dev/null)" = screenrecord ]; then '
            'start=$(awk \'{print $22}\' /proc/$pid/stat); '
            'echo "$pid $start"; exit 0; fi; '
            'n=$((n+1)); sleep 0.1; done; exit 1'
        )
        output = self.device('shell', script).decode().strip()
        if not re.fullmatch(r'[1-9][0-9]* [0-9]+', output):
            raise RuntimeError('Could not identify screen recorder process')
        pid, starttime = output.split()
        self.recording = dict(recording_id=recording_id, path=path, pid=pid, starttime=starttime)
        return dict(recording_id=recording_id, max_seconds=120)

    def stop_recording(self, recording_id):
        _identifier(recording_id)
        if self.recording is None or self.recording['recording_id'] != recording_id:
            raise ValueError('Unknown active recording')
        recording = self.recording
        pid, starttime, path = recording['pid'], recording['starttime'], recording['path']
        script = (
            '# stop-recording\n'
            f'if [ -r /proc/{pid}/stat ]; then '
            f'if [ "$(awk \'{{print $22}}\' /proc/{pid}/stat)" != {starttime} ]; then '
            'echo identity-mismatch; exit 0; fi; '
            f'if [ "$(awk \'{{print $3}}\' /proc/{pid}/stat)" = Z ]; then '
            'echo finished; exit 0; fi; '
            f'if [ "$(cat /proc/{pid}/comm)" != screenrecord ] || '
            f'! tr "\\000" " " < /proc/{pid}/cmdline | grep -F -- {path} >/dev/null; then '
            'echo identity-mismatch; exit 0; fi; '
            f'kill -2 {pid}; '
            'n=0; while [ "$n" -lt 50 ]; do '
            f'if [ ! -r /proc/{pid}/stat ] || '
            f'[ "$(awk \'{{print $3}}\' /proc/{pid}/stat 2>/dev/null)" = Z ]; then '
            'echo stopped; exit 0; fi; n=$((n+1)); sleep 0.1; done; '
            'echo stop-timeout; else echo finished; fi'
        )
        result = self.device('shell', script).decode().strip()
        if result not in ['stopped', 'finished']:
            raise RuntimeError(f'Recorder {result or "stop failed"}')
        # Once stopped, release the active-recorder slot even if publication
        # fails. The generated remote path is removed in that case too.
        self.recording = None
        try:
            size = self.device('shell', f'wc -c < {path}').decode().strip()
            if not size.isdigit() or not 0 < int(size) <= MAX_BYTES:
                raise ValueError('Invalid recording size')
            data = self.device('exec-out', 'cat', path)
            if len(data) != int(size) or b'ftyp' not in data[:32]:
                raise ValueError('Device did not return a finalized MP4')
            return self._publish(data, 'video/mp4', '.mp4', 'walkthrough')
        finally:
            self.device('shell', f'rm -f {path}')

    def close(self):
        """Best-effort recording finalization; cleanup errors remain observable."""
        if self.recording:
            try:
                self.stop_recording(self.recording['recording_id'])
            except Exception as error:
                self.errors.append(str(error))
        return dict(artifacts=self.list_artifacts(), errors=list(self.errors))
