import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys

spec = importlib.util.spec_from_file_location('measure', Path(__file__).resolve().parents[2]/'scripts/measure-rollouts.py')
measure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(measure)


class MeasurementTests(unittest.TestCase):
    def report(self, records, **kwargs):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'secret-session.jsonl'
            path.write_text('\n'.join(json.dumps(record) if not isinstance(record, str) else record for record in records))
            result = measure.measure([path, path], **kwargs)
        text = json.dumps(result)
        self.assertNotIn('TOP_SECRET', text)
        self.assertNotIn('secret-session', text)
        return result

    def test_calls_wrappers_mirrors_reads_and_images(self):
        records = [
            dict(type='session_meta', payload=dict(id='session', source='cli')),
            dict(type='response_item', payload=dict(type='function_call', call_id='one', name='exec_command', arguments=json.dumps(dict(cmd='cat TOP_SECRET')))),
            dict(type='event_msg', payload=dict(type='tool_call', call_id='one', name='exec_command', arguments='mirror')),
            dict(type='response_item', payload=dict(type='function_call_output', call_id='one', output='Warning: truncated output\nTOP_SECRET')),
            dict(type='response_item', payload=dict(type='function_call', call_id='two', name='exec_command', arguments=json.dumps(dict(cmd='cat TOP_SECRET')))),
            dict(type='response_item', payload=dict(type='function_call_output', call_id='two', output=json.dumps(dict(content=[dict(type='text', text='TOP_SECRET'), dict(type='image', data='TOP_SECRET')])))),
            dict(type='response_item', payload=dict(type='message', content=[dict(type='input_image', image_url='TOP_SECRET')])),
            dict(type='response_item', payload=dict(type='function_call', call_id='wrapper', name='parallel', arguments=json.dumps(dict(tool_uses=[dict(recipient_name='functions.exec_command', parameters=dict(cmd='cat TOP_SECRET'))])))),
            dict(type='response_item', payload=dict(type='function_call', call_id='code', name='codemode_exec_abc', arguments=json.dumps(dict(code='await tools.get_status({job_id:"TOP_SECRET"})')))),
        ]
        report = self.report(records)
        self.assertEqual(report['calls']['confirmed'], 2)
        self.assertEqual(report['calls']['submitted'], 4)
        self.assertEqual(report['calls']['static_references'], 2)
        self.assertEqual(report['repeated_reads'], 1)
        self.assertEqual(report['images'], 2)
        self.assertEqual(report['truncated_outputs'], 1)
        self.assertEqual(report['duplicate_records'], 1)
        self.assertGreater(report['argument_characters'], 20)
        self.assertIsNone(report['usage']['parent']['input_peak'])

    def test_cumulative_usage_compaction_workers_and_malformed(self):
        usage = dict(type='event_msg', payload=dict(type='token_count', info=dict(
            total_token_usage=dict(input_tokens=100, output_tokens=20),
            last_token_usage=dict(input_tokens=80, output_tokens=10))))
        report = self.report([
            dict(timestamp='2026-11-25T10:00:00Z', type='session_meta', payload=dict(id='session')),
            usage, usage,
            dict(type='compacted', payload=dict(message='TOP_SECRET')),
            '{malformed',
            dict(timestamp='2026-11-25T10:01:00Z', type='event_msg', payload=dict(type='token_count', info=dict(
                total_token_usage=dict(input_tokens=130, output_tokens=30), last_token_usage=dict(input_tokens=15, output_tokens=10)))),
        ])
        self.assertEqual(report['usage']['parent']['input_tokens'], 130)
        self.assertEqual(report['usage']['parent']['input_peak'], 80)
        self.assertEqual(report['compactions'], 1)
        self.assertEqual(report['malformed_records'], 1)
        self.assertEqual(report['elapsed_seconds'], 60)
        self.assertTrue(report['incomplete_telemetry'])
        worker = self.report([dict(type='session_meta', payload=dict(id='worker', source=dict(subagent=dict(thread_spawn=dict(parent_thread_id='parent'))))), usage])
        self.assertEqual(worker['usage']['worker']['input_tokens'], 100)
        self.assertIsNone(worker['usage']['parent']['input_tokens'])

    def test_json_wrappers_and_non_records_are_safe(self):
        report = self.report([None, [], dict(type='response_item', payload=dict(type='function_call_output', call_id='unknown', output=json.dumps(dict(result=dict(content=[dict(type='text', text='TOP_SECRET')])))))])
        self.assertEqual(report['output_characters'], len('TOP_SECRET'))
        self.assertEqual(report['malformed_records'], 2)
        self.assertEqual(report['calls']['confirmed'], 0)

    def test_mirrored_and_extended_sessions_merge_without_losing_calls(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            base = [dict(type='session_meta', payload=dict(id='session')),
                    dict(type='response_item', payload=dict(type='function_call', call_id='first', name='get_status', arguments='{}')),
                    dict(type='response_item', payload=dict(type='function_call_output', call_id='first', output='first'))]
            extra = [dict(type='response_item', payload=dict(type='function_call', call_id='second', name='read_logs', arguments='{}')),
                     dict(type='response_item', payload=dict(type='function_call_output', call_id='second', output='second'))]
            first, mirror, extended = (root/name for name in ('first', 'mirror', 'extended'))
            first.write_text('\n'.join(map(json.dumps, base)))
            mirror.write_bytes(first.read_bytes())
            extended.write_text('\n'.join(map(json.dumps, base+extra)))
            report = measure.measure([first, mirror, extended])
            self.assertEqual(report['files'], 1)
            self.assertEqual(report['duplicate_files'], 1)
            self.assertEqual(report['calls']['confirmed'], 2)
            self.assertEqual(report['output_characters'], len('firstsecond'))
            self.assertEqual(report['operations'], dict(get_status=1, read_logs=1))

    def test_wrapper_results_and_unknown_names_never_export_content(self):
        report = self.report([
            dict(type='response_item', payload=dict(type='function_call', call_id='one', name='TOP_SECRET', arguments='{}')),
            dict(type='response_item', payload=dict(type='function_call_output', call_id='one', output=json.dumps(dict(results=[dict(result=dict(content=[dict(type='text', text='TOP_SECRET')]))])))),
        ])
        self.assertEqual(report['operations'], dict(other=1))
        self.assertEqual(report['output_characters'], len('TOP_SECRET'))

    def test_cli_privacy_formats_and_invalid_encoding(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'TOP_SECRET.jsonl'
            path.write_text(json.dumps(dict(type='response_item', payload=dict(
                type='function_call_output', call_id='call', output='Wall time: 1.5 seconds\nTOP_SECRET'))))
            for format_name in ('json', 'markdown'):
                completed = subprocess.run([sys.executable, str(Path(measure.__file__)), str(path), '--format', format_name], capture_output=True, text=True, check=True)
                self.assertNotIn('TOP_SECRET', completed.stdout+completed.stderr)
                self.assertIn('output_characters', completed.stdout)
            report = measure.measure([path])
            self.assertEqual(report['tool_wall_seconds'], 1.5)
            path.write_bytes(b'\xff\xfe')
            self.assertEqual(measure.measure([path])['malformed_records'], 1)

    def test_task_references_are_not_workflow_executions(self):
        records = []
        for index, command in enumerate(('cat .mise/tasks/check', 'echo "bash .mise/tasks/check"', 'bash .mise/tasks/check')):
            records.extend([
                dict(type='response_item', payload=dict(type='function_call', call_id=str(index), name='exec_command', arguments=json.dumps(dict(cmd=command)))),
                dict(type='response_item', payload=dict(type='function_call_output', call_id=str(index), output='result')),
            ])
        report = self.report(records)
        self.assertEqual(report['operations'], dict(check=1, shell=2))


if __name__ == '__main__':
    unittest.main()
