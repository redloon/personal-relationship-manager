#!/usr/bin/env python3
"""Usage: .mise/tasks/qa-report MANIFEST [--output PATH] [--evidence JSON]."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote


def text(value):
    """Keep captions/evidence as literal Markdown, not injected links or HTML."""
    value = str(value).replace('\r', ' ').replace('\n', ' ')
    value = value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    return re.sub(r'([\\`*_{}\[\]()!|#])', r'\\\1', value)


def digest(value, length=64):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{'+str(length)+'}', value):
        raise ValueError('Invalid manifest identifier/hash')
    return value


def inside(path, directory):
    path = path.resolve(strict=True)
    if not path.is_relative_to(directory):
        raise ValueError('Artifact path escapes manifest directory')
    return path


def load_artifacts(manifest):
    data = json.loads(manifest.read_text())
    owner = digest(data['owner_id'], 32)
    artifacts = data['artifacts']
    if not isinstance(artifacts, list) or len(artifacts) > 1000:
        raise ValueError('Invalid artifact list')
    root = manifest.parent.resolve()
    verified = []
    seen = set()
    for item in artifacts:
        identifier = digest(item['artifact_id'], 32)
        if identifier in seen or item['owner_id'] != owner:
            raise ValueError('Duplicate artifact or mismatched owner')
        seen.add(identifier)
        for field in ['sha256', 'app_sha256', 'test_sha256']:
            digest(item[field])
        suffix = {'image/png': '.png', 'video/mp4': '.mp4'}.get(item['mime_type'])
        if suffix is None:
            raise ValueError('Unsupported artifact MIME type')
        label, timestamp = item['label'], item['captured_at']
        if not isinstance(label, str) or len(label) > 200 or not isinstance(timestamp, str):
            raise ValueError('Invalid artifact caption/timestamp')
        relative = Path(item['relative_path'])
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Invalid relative artifact path')
        # Exported manifests live at OUTBOX/OWNER/manifest.json, while their
        # relative paths are relative to OUTBOX. Also allow a portable manifest
        # whose paths are directly relative to its own directory.
        parts = relative.parts
        if len(parts) == 2 and parts[0] == owner and root.name == owner:
            relative = Path(parts[1])
        path = inside(root/relative, root)
        if path.name != identifier+suffix:
            raise ValueError('Artifact filename does not match its identifier/type')
        shared = item.get('shared_path')
        if shared is not None and inside(Path(shared), root) != path:
            raise ValueError('Shared artifact path mismatch')
        size = item['bytes']
        if type(size) is not int or not 0 < size <= 128*1024*1024:
            raise ValueError('Invalid artifact size')
        actual = hashlib.sha256()
        with path.open('rb') as source:
            for chunk in iter(lambda: source.read(1024*1024), b''):
                actual.update(chunk)
        if path.stat().st_size != size or actual.hexdigest() != item['sha256']:
            raise ValueError('Artifact size/hash mismatch')
        verified.append((item, path))
    return owner, verified


def evidence_lines(evidence):
    if not isinstance(evidence, dict):
        raise ValueError('Evidence must be a JSON object')
    lines = ['## Journey evidence', '', 'Reported status: **'+text(evidence.get('status', 'unspecified'))+'**.', '']
    if evidence.get('error'):
        lines += [text(evidence['error']), '']
    if evidence.get('failed_step') is not None:
        lines += ['Failed step index (zero-based): '+text(evidence['failed_step']), '']
    steps = evidence.get('steps', evidence.get('results', []))
    if not isinstance(steps, list):
        raise ValueError('Evidence steps/results must be a list')
    for index, step in enumerate(steps):
        if not isinstance(step, dict):
            raise ValueError('Evidence step must be an object')
        action = step.get('action', step.get('step', 'step'))
        if isinstance(action, dict):
            action = action.get('action', 'step')
        status = step.get('status', 'verified' if step.get('verified') is True else 'action sent; outcome not asserted')
        line = f"{index+1}. {text(action)} — {text(status)}"
        if step.get('error'):
            line += ': '+text(step['error'])
        lines.append(line)
    if not steps:
        lines.append('No per-step outcomes supplied.')
    return lines + ['']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    try:
        manifest = args.manifest.resolve(strict=True)
        owner, artifacts = load_artifacts(manifest)
        output = (args.output or manifest.parent/'review.md').resolve()
        protected = {manifest, *(path for _, path in artifacts)}
        if args.evidence:
            protected.add(args.evidence.resolve())
        if output in protected:
            raise ValueError('Report output cannot overwrite manifest, evidence or capture')
        lines = ['# Android QA review', '', f'Session/job: `{owner}`', '',
                 'Interactive evidence is not a regression-suite pass. Captures show only the states recorded; '
                 'check the accompanying test report for regression coverage.', '']
        hashes = sorted({(item['app_sha256'], item['test_sha256']) for item, _ in artifacts})
        for app, test in hashes:
            lines += [f'App APK SHA-256: `{app}`', '', f'Test APK SHA-256: `{test}`', '']
        if args.evidence:
            lines += evidence_lines(json.loads(args.evidence.read_text()))
        else:
            lines += ['No journey assertions or regression results supplied.', '']
        lines += ['## Captures', '']
        if not artifacts:
            lines += ['No captures were exported.', '']
        for item, path in artifacts:
            label = text(item['label'])
            link = quote(os.path.relpath(path, output.parent), safe='/.-_')
            lines += [f"### {label}", '', text(item['captured_at']), '']
            if item['mime_type'] == 'image/png':
                lines.append(f'![{label}](<{link}>)')
            else:
                lines.append(f'[Watch {label}](<{link}>)')
            lines += ['', f"{item['mime_type']}, {item['bytes']} bytes; SHA-256: `{item['sha256']}`", '']
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text('\n'.join(lines).rstrip()+'\n')
        print(output)
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f'qa-report: {error}\n')


if __name__ == '__main__':
    main()
