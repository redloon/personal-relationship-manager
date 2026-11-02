"""Single-owner interactive sessions; device work stays on the lock-owning thread."""
import base64
import copy
import json
from pathlib import Path
import queue
import threading
import time
import uuid


class InteractiveSession:
    def __init__(self, owner, session_id):
        self.owner = owner
        self.id = session_id
        self.requests = queue.Queue()
        self.pending = False
        self.closed = False
        self.mutex = threading.Lock()
        self.started = self.touched = time.monotonic()
        self.batch_cancelled = threading.Event()
        self.current_batch = None
        self.interaction = self.captures = None

    def check(self):
        if self.owner.cancelled.is_set():
            raise RuntimeError('session cancelled')
        now = time.monotonic()
        if now-self.started > 2700:
            raise RuntimeError('maximum session duration exceeded')
        if now-self.touched > 600:
            raise RuntimeError('session idle timeout')

    def check_batch(self):
        self.check()
        if self.batch_cancelled.is_set():
            raise RuntimeError('batch cancelled')
        if time.monotonic() > self.batch_deadline:
            raise RuntimeError('batch deadline exceeded')

    def enqueue(self, operation, **arguments):
        with self.mutex:
            self.check()
            if self.closed:
                raise ValueError('session closed')
            if self.pending:
                raise ValueError('session has a pending operation')
            self.pending = True
            if operation == 'batch':
                self.batch_cancelled.clear()
            self.owner.jobs[self.id]['operation_pending'] = operation
            self.owner.save(self.id)
            self.touched = time.monotonic()
            request = dict(operation=operation, arguments=arguments, done=threading.Event())
            self.requests.put(request)
            return request

    def process(self, request):
        try:
            self.check()
            op, args = request['operation'], request['arguments']
            if op == 'inspect':
                snapshot = self.interaction.inspect()
                artifact = self.captures.screenshot('inspection')
                request['result'] = dict(snapshot=snapshot, artifact=artifact)
            elif op == 'start_recording':
                request['result'] = self.captures.start_recording()
            elif op == 'stop_recording':
                request['result'] = self.captures.stop_recording(args['recording_id'])
            elif op == 'batch':
                self.run_batch(args['batch_id'], args['steps'])
            else:
                raise ValueError('Unknown session operation')
        except Exception as error:
            request['error'] = str(error)
            if request['operation'] == 'batch':
                bid = request['arguments']['batch_id']
                self.owner.jobs[bid].update(status='failed', phase='finished', error=str(error))
                self.owner.save(bid)
        finally:
            with self.mutex:
                self.pending = False
                self.owner.jobs[self.id].pop('operation_pending', None)
                self.owner.save(self.id)
                self.touched = time.monotonic()
            request['done'].set()

    def run_batch(self, batch_id, steps):
        self.current_batch = batch_id
        self.batch_deadline = time.monotonic()+120
        self.interaction.check = self.check_batch
        job = self.owner.jobs[batch_id]
        job.update(status='running', phase='actions', results=[])
        try:
            for index, step in enumerate(steps):
                self.check_batch()
                if step['action'] == 'screenshot':
                    outcome = self.captures.screenshot(step.get('label', 'checkpoint'))
                else:
                    outcome = self.interaction.perform(step)
                job['results'].append(dict(action=step['action'], **{k:v for k,v in outcome.items() if k != 'action'}))
                self.owner.save(batch_id)
            job.update(status='passed', phase='finished')
        except Exception as error:
            job.update(status='cancelled' if self.batch_cancelled.is_set() or self.owner.cancelled.is_set() else 'failed',
                       phase='finished', error=str(error), failed_step=len(job['results']))
        finally:
            self.interaction.check = self.check
            # Capture evidence even after a cancelled batch; session cancellation
            # still proceeds directly to cleanup.
            if not self.owner.cancelled.is_set():
                try:
                    job['final_snapshot'] = self.interaction.inspect()
                except Exception as error:
                    job['snapshot_error'] = str(error)
                try:
                    job['artifact'] = self.captures.screenshot('batch-'+job['status'])
                except Exception as error:
                    job['capture_error'] = str(error)
            self.owner.save(batch_id)
            self.current_batch = None

    def run(self, device_binary):
        from interaction import Interaction
        from captures import Captures
        job = self.owner.jobs[self.id]
        self.captures = Captures(self.owner.state/self.id, self.owner.outbox, self.id,
                                 job['app_sha256'], job['test_sha256'], device_binary)
        def device(*args):
            self.check()
            return device_binary(*args).decode(errors='replace')
        self.interaction = Interaction(device, self.check)
        job.update(phase='ready')
        self.owner.save(self.id)
        try:
            while True:
                self.check()
                try:
                    request = self.requests.get(timeout=.5)
                except queue.Empty:
                    continue
                self.process(request)
        finally:
            self.close()

    def close(self):
        with self.mutex:
            if self.closed:
                return
            self.closed = True
            while True:
                try:
                    request = self.requests.get_nowait()
                except queue.Empty:
                    break
                if request['operation'] == 'batch':
                    bid = request['arguments']['batch_id']
                    self.owner.jobs[bid].update(status='cancelled', phase='finished', error='session closed')
                    self.owner.save(bid)
                request['error'] = 'session closed'
                request['done'].set()
            self.pending = False
        if self.captures:
            result = self.captures.close()
            if isinstance(result, dict) and result.get('errors'):
                self.owner.jobs[self.id]['capture_errors'] = result['errors']
                self.owner.save(self.id)


class SessionTools:
    """Mixed into Runner; JSON persisted statuses survive transport reconnects."""
    def start_session(self, app_sha256: str, test_sha256: str):
        return self._submit(app_sha256, test_sha256, None, interactive=True)

    def _session(self, session_id):
        status = self.get_status(session_id)
        if status.get('kind') != 'session' or status['status'] != 'running':
            raise ValueError('Not a running interactive session')
        session = self.sessions.get(session_id)
        if session is None:
            raise ValueError('Session belongs to another runner connection; reconnect to its owner')
        if status['phase'] != 'ready':
            raise ValueError('Session is not ready; poll get_status')
        return session

    def _request(self, session_id, operation, **arguments):
        request = self._session(session_id).enqueue(operation, **arguments)
        if not request['done'].wait(55):
            raise RuntimeError('Operation still pending; poll session status before another action')
        if 'error' in request:
            raise ValueError(request['error'])
        return request.get('result', {})

    def inspect(self, session_id: str):
        result = self._request(session_id, 'inspect')
        return self._image_result(result, session_id, result['artifact']['artifact_id'])

    def execute_steps(self, session_id: str, steps: list[dict]):
        from interaction import validate_steps
        validate_steps(steps)
        session = self._session(session_id)
        bid = uuid.uuid4().hex
        (self.state/bid).mkdir(mode=0o700)
        self.jobs[bid] = dict(kind='batch', session_id=session_id, status='running', phase='queued')
        self.save(bid)
        try:
            session.enqueue('batch', batch_id=bid, steps=copy.deepcopy(steps))
        except Exception:
            self.jobs[bid].update(status='cancelled', phase='finished', error='submission refused')
            self.save(bid)
            raise
        return dict(job_id=bid, session_id=session_id)

    def end_session(self, session_id: str):
        self._session(session_id)
        self.jobs[session_id]['end_requested'] = True
        self.save(session_id)
        self.cancelled.set()
        return dict(session_id=session_id, status='closing', instruction='Poll get_status until cleanup completes')

    def start_recording(self, session_id: str):
        return self._request(session_id, 'start_recording')

    def stop_recording(self, session_id: str, recording_id: str):
        return self._request(session_id, 'stop_recording', recording_id=recording_id)

    def _captures(self, owner_id):
        from captures import Captures
        status = self.get_status(owner_id)
        if status.get('kind') == 'batch':
            owner_id = status['session_id']
            status = self.get_status(owner_id)
        session = self.sessions.get(owner_id)
        if session and session.captures:
            return session.captures
        def unavailable(*args):
            raise ValueError('Offline artifact access cannot run device commands')
        return Captures(self.state/owner_id, self.outbox, owner_id,
                        status.get('app_sha256', ''), status.get('test_sha256', ''), unavailable)

    def list_artifacts(self, job_id: str):
        return dict(artifacts=self._captures(job_id).list_artifacts())

    def _image_result(self, result, owner_id, artifact_id):
        from mcp.types import CallToolResult, TextContent, ImageContent
        file = self._captures(owner_id).artifact_file(artifact_id)
        return CallToolResult(content=[TextContent(type='text', text=json.dumps(result)),
            ImageContent(type='image', mimeType='image/png', data=base64.b64encode(file.read_bytes()).decode())])

    def get_artifact(self, job_id: str, artifact_id: str):
        captures = self._captures(job_id)
        entries = [a for a in captures.list_artifacts() if a['artifact_id'] == artifact_id]
        if not entries:
            raise ValueError('Unknown artifact')
        artifact = entries[0]
        if artifact['mime_type'] == 'image/png':
            return self._image_result(artifact, job_id, artifact_id)
        # Videos stay in shared files; never inline megabytes into tool text.
        return artifact
