#!/usr/bin/env python3
"""SDK-free, aggregate-only measurement of explicitly selected Codex JSONL files."""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True).encode()).digest()


def decode(value):
    if isinstance(value, str) and value.lstrip().startswith(('{', '[')):
        try:
            return json.loads(value)
        except (ValueError, RecursionError):
            pass
    return value


def text_and_images(value, depth=0):
    if depth > 12:
        return '', 0
    decoded = decode(value)
    if isinstance(decoded, str):
        return decoded, 0
    if isinstance(decoded, list):
        parts = [text_and_images(item, depth+1) for item in decoded]
        return ''.join(part[0] for part in parts), sum(part[1] for part in parts)
    if not isinstance(decoded, dict):
        return '', 0
    if decoded.get('type') in ('image', 'input_image', 'output_image', 'image_url'):
        return '', 1
    if isinstance(decoded.get('text'), str):
        return decoded['text'], 0
    for key in ('content', 'output', 'result', 'results', 'tool_responses'):
        if key in decoded:
            return text_and_images(decoded[key], depth+1)
    return '', 0


def operation(name, arguments):
    short = name.rsplit('.', 1)[-1]
    if short in ('get_status', 'read_logs', 'start_test', 'start_session', 'execute_steps', 'inspect',
                 'start_recording', 'stop_recording', 'get_artifact', 'start_recovery_test', 'spawn_agent'):
        return short
    command = arguments.get('cmd', arguments.get('command', '')) if isinstance(arguments, dict) else ''
    if isinstance(command, list):
        command = ' '.join(str(part) for part in command)
    if short in ('exec_command', 'write_stdin'):
        if isinstance(command, str):
            try:
                words = shlex.split(command)
            except ValueError:
                words = []
            if words[:1] == ['bash']:
                words = words[1:]
            for task in ('check', 'test-stage', 'tooling-test', 'qa-report'):
                if words[:1] == ['.mise/tasks/'+task] or words[:3] == ['mise', 'run', task]:
                    return task
        return 'shell'
    return 'other'


def measure(paths):
    result = dict(files=0, duplicate_files=0, duplicate_records=0, malformed_records=0,
                  calls=dict(submitted=0, confirmed=0, static_references=0),
                  argument_characters=0, output_characters=0, output_utf8_bytes=0,
                  shell_output_characters=0, shell_output_utf8_bytes=0,
                  images=0, truncated_outputs=0, repeated_reads=0, compactions=0,
                  operations={}, usage={}, elapsed_seconds=None, tool_wall_seconds=None, incomplete_telemetry=True)
    seen_files, seen_contents, sessions = set(), set(), {}
    aggregate_usage = {'parent': [], 'worker': []}
    operation_counts = Counter()
    elapsed = []
    wall_times = []
    for raw_path in paths:
        path = Path(raw_path).resolve()
        if path in seen_files:
            result['duplicate_files'] += 1
            continue
        seen_files.add(path)
        records = []
        try:
            with path.open() as source:
                for line in source:
                    try:
                        record = json.loads(line)
                        if not isinstance(record, dict) or not isinstance(record.get('payload'), dict):
                            raise ValueError()
                        records.append(record)
                    except (ValueError, RecursionError):
                        result['malformed_records'] += 1
        except (OSError, UnicodeError):
            result['malformed_records'] += 1
            continue
        metadata = next((record['payload'] for record in records if record.get('type') == 'session_meta'), {})
        content_digest = fingerprint(records)
        if content_digest in seen_contents:
            result['duplicate_files'] += 1
            continue
        seen_contents.add(content_digest)
        session_id = metadata.get('id')
        identity = session_id if isinstance(session_id, str) else str(path)
        if identity in sessions:
            existing = sessions[identity]
            for record in records:
                signature = fingerprint(record)
                if signature in existing['seen_records']:
                    result['duplicate_records'] += 1
                else:
                    existing['records'].append(record)
                    existing['seen_records'].add(signature)
        else:
            sessions[identity] = dict(metadata=metadata, records=records, seen_records={fingerprint(record) for record in records})
    for session in sessions.values():
        records = session['records']
        metadata = session['metadata']
        result['files'] += 1
        role = 'worker' if isinstance(metadata.get('source'), dict) and 'subagent' in metadata['source'] else 'parent'
        calls, outputs, reads, seen_events, timestamps = {}, set(), set(), set(), []
        shell_calls, output_volumes = set(), {}
        totals, peaks = {}, {}
        for record in records:
            timestamp = record.get('timestamp')
            if isinstance(timestamp, str):
                try:
                    timestamps.append(datetime.fromisoformat(timestamp.replace('Z', '+00:00')).timestamp())
                except (ValueError, OverflowError):
                    pass
            payload = record['payload']
            kind = payload.get('type')
            call_id = payload.get('call_id')
            if kind in ('function_call', 'custom_tool_call', 'tool_call') and isinstance(call_id, str):
                if call_id in calls:
                    result['duplicate_records'] += 1
                    continue
                name = payload.get('name', '')
                if not isinstance(name, str):
                    name = ''
                raw = payload.get('arguments', payload.get('input', ''))
                arguments = decode(raw)
                calls[call_id] = operation(name, arguments)
                if name.rsplit('.', 1)[-1] in ('exec_command', 'write_stdin'):
                    shell_calls.add(call_id)
                result['calls']['submitted'] += 1
                result['argument_characters'] += len(raw) if isinstance(raw, str) else len(json.dumps(raw))
                if isinstance(arguments, dict):
                    nested = arguments.get('tool_uses', [])
                    if isinstance(nested, list):
                        result['calls']['static_references'] += len(nested)
                    code = arguments.get('code', '')
                    if isinstance(code, str):
                        result['calls']['static_references'] += len(re.findall(r'\btools\.\w+\s*\(', code))
                    command = arguments.get('cmd', arguments.get('command'))
                    if isinstance(command, str) and re.match(r'^\s*(?:cat|sed|rg|head|tail)\s', command):
                        signature = fingerprint(command)
                        result['repeated_reads'] += signature in reads
                        reads.add(signature)
            elif kind in ('function_call_output', 'custom_tool_call_output', 'tool_result'):
                identity = call_id if isinstance(call_id, str) else fingerprint(payload)
                if identity in outputs:
                    result['duplicate_records'] += 1
                    continue
                outputs.add(identity)
                text, images = text_and_images(payload.get('output', payload.get('content', '')))
                result['output_characters'] += len(text)
                result['output_utf8_bytes'] += len(text.encode('utf-8', errors='replace'))
                output_volumes[identity] = (len(text), len(text.encode('utf-8', errors='replace')))
                result['images'] += images
                result['truncated_outputs'] += bool(re.search(r'truncated|chars omitted|tokens truncated', text, re.I))
                wall_time = re.search(r'^Wall time:\s*(\d+(?:\.\d+)?)\s*seconds?\s*$', text, re.MULTILINE)
                if wall_time:
                    seconds = float(wall_time[1])
                    if 0 <= seconds <= 1e12:
                        wall_times.append(seconds)
            elif kind == 'token_count':
                signature = fingerprint(payload)
                if signature in seen_events:
                    result['duplicate_records'] += 1
                    continue
                seen_events.add(signature)
                info = payload.get('info')
                if isinstance(info, dict):
                    for field, destination in (('total_token_usage', totals), ('last_token_usage', peaks)):
                        usage = info.get(field)
                        if isinstance(usage, dict):
                            for key in ('input_tokens', 'output_tokens', 'cached_input_tokens', 'reasoning_output_tokens'):
                                value = usage.get(key)
                                if type(value) is int and value >= 0:
                                    destination[key] = max(destination.get(key, 0), value)
            elif record.get('type') == 'compacted' or kind == 'context_compacted':
                result['compactions'] += 1
            elif kind == 'message':
                result['images'] += text_and_images(payload.get('content'))[1]
        confirmed = set(calls) & outputs
        for call_id in confirmed & shell_calls:
            characters, byte_count = output_volumes[call_id]
            result['shell_output_characters'] += characters
            result['shell_output_utf8_bytes'] += byte_count
        result['calls']['confirmed'] += len(confirmed)
        operation_counts.update(calls[call_id] for call_id in confirmed)
        aggregate_usage[role].append(dict(totals=totals, peaks=peaks))
        if len(timestamps) > 1:
            elapsed.append(max(timestamps)-min(timestamps))
    for role, sessions in aggregate_usage.items():
        result['usage'][role] = {
            key: sum(session['totals'][key] for session in sessions if key in session['totals'])
            if any(key in session['totals'] for session in sessions) else None
            for key in ('input_tokens', 'output_tokens', 'cached_input_tokens', 'reasoning_output_tokens')}
        result['usage'][role]['input_peak'] = max((session['peaks']['input_tokens'] for session in sessions if 'input_tokens' in session['peaks']), default=None)
    result['operations'] = dict(sorted(operation_counts.items()))
    result['elapsed_seconds'] = round(sum(elapsed), 3) if elapsed else None
    result['tool_wall_seconds'] = round(sum(wall_times), 3) if wall_times else None
    result['limitations'] = [
        'Partial telemetry: submitted calls are not proof of execution; confirmed means a matching result record, not success.',
        'Wrapper/code references are static only; dynamic calls and task intent may be unobservable.',
        'Workflow counts recognize direct leading canonical-task invocations only, not references or completed validation gates.',
        'Repeated reads mean identical read-command strings, not verified unchanged file contents.',
        'Usage uses per-session cumulative maxima, not sums of cumulative events; resets can undercount.',
        'Elapsed is the sum of observed file spans, not exclusive wall time; parent and worker spans can overlap.',
        'Tool wall time sums recognized output headers only; it is neither CPU time nor full workflow timing.',
        'Text volume excludes images; characters are not tokens. Missing telemetry is null, never inferred zero.',
        'No raw text, arguments, paths, session identifiers or arbitrary operation names are exported.',
    ]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('paths', type=Path, nargs='+')
    parser.add_argument('--format', choices=('json', 'markdown'), default='markdown')
    args = parser.parse_args()
    report = measure(args.paths)
    if args.format == 'json':
        print(json.dumps(report, indent=2))
    else:
        print('# Rollout measurement\n\nAggregate metrics (incomplete telemetry):\n\n```json')
        print(json.dumps({key: value for key, value in report.items() if key != 'limitations'}, indent=2))
        print('```\n')
        for limitation in report['limitations']:
            print('- '+limitation)


if __name__ == '__main__':
    main()
