"""Process-death contract and failure probes without Android SDK execution."""
import hashlib
import json
from pathlib import Path
import tempfile
import threading
import subprocess
import unittest
from unittest.mock import Mock, patch

import runner


class RecoveryTests(unittest.TestCase):
    def test_taps_use_the_current_verified_snapshot_without_redundant_dumps(self):
        from recovery import RecoveryJourney
        commands=[]
        def device(*args):
            commands.append(args)
            if args[:2]==('exec-out','uiautomator'):
                return '<hierarchy><node text="Save" resource-id="save" bounds="[0,0][100,50]" enabled="true" clickable="true" /></hierarchy>'
            if args==('shell','wm','size'):return 'Physical size: 320x640'
            return 'Success'
        journey=RecoveryJourney(device)
        journey.tap('save')
        self.assertIsNone(journey.ui.snapshot)  # Every input still invalidates refs.
        journey.ui.inspect()
        journey.text_tap('Save')
        dumps=[args for args in commands if args[:2]==('exec-out','uiautomator')]
        self.assertEqual(len(dumps),2)  # One fresh wait; one explicitly inspected snapshot.
        self.assertEqual(commands.count(('shell','input','tap','50','25')),2)

    def test_cached_taps_preserve_ambiguity_and_disabled_control_rejection(self):
        from recovery import RecoveryJourney
        for nodes in [
            '<node text="Save" resource-id="save" bounds="[0,0][100,50]" enabled="false" />',
            '<node text="Save" resource-id="save" bounds="[0,0][100,50]" /><node text="Save" resource-id="other" bounds="[0,50][100,100]" />',
        ]:
            with self.subTest(nodes=nodes):
                def device(*args):
                    if args[:2]==('exec-out','uiautomator'):return '<hierarchy>'+nodes+'</hierarchy>'
                    if args==('shell','wm','size'):return 'Physical size: 320x640'
                    self.fail('Unsafe input was sent')
                journey=RecoveryJourney(device)
                journey.ui.inspect()
                with self.assertRaises(ValueError):journey.text_tap('Save')

    def test_public_tool_validates_cases_deadlines_and_app_only_submission(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            instance=runner.Runner(dict(sdk=str(root/'sdk'),inbox=str(root/'inbox'),state=str(root/'jobs'),avd='android_tests'))
            with patch.object(instance,'_submit') as submit:
                instance.start_recovery_test('a'*64)
                self.assertIsNone(submit.call_args.args[1])
                self.assertTrue(submit.call_args.kwargs['recovery'])
                for case in ['saved_records','edited_records','deleted_records']:
                    instance.start_recovery_test('a'*64,case,42)
                    self.assertEqual(submit.call_args.kwargs['case'],case)
                for invalid in ['drafts','',True,12]:
                    with self.assertRaises(ValueError):instance.start_recovery_test('a'*64,invalid)
                for invalid in [0,901,True,1.5]:
                    with self.assertRaises(ValueError):instance.start_recovery_test('a'*64,timeout_seconds=invalid)
            self.assertEqual(instance.health()['version'],6)
            self.assertIn('recovery',instance.health()['capabilities'])

    def test_private_app_copy_verifies_hash_and_retains_original_release_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);inbox=root/'inbox';inbox.mkdir()
            (inbox/'app.apk').write_bytes(b'app')
            digest=hashlib.sha256(b'app').hexdigest()
            release=dict(app_sha256=digest,version='0.1.0',version_code=2,certificate_sha256='c'*64,
                         application_id=runner.PACKAGE,source_sha256='a'*64,git_revision='b'*40,apk='app.apk')
            provenance=dict(source_sha256='a'*64,git_revision='b'*40,release_manifest=release)
            (inbox/'manifest.json').write_text(json.dumps(dict(app_sha256=digest,provenance=provenance)))
            instance=runner.Runner(dict(sdk=str(root/'sdk'),inbox=str(inbox),state=str(root/'jobs'),avd='android_tests'))
            with patch.object(runner.threading,'Thread') as thread:
                jid=instance.start_recovery_test(digest)['job_id']
                thread.call_args.kwargs['args'][2].close()
            job=instance.get_status(jid)
            self.assertEqual(job['kind'],'recovery')
            self.assertEqual(job['provenance'],provenance)
            self.assertFalse((root/'jobs'/jid/'test.apk').exists())
            self.assertEqual((root/'jobs'/jid/'app.apk').read_bytes(),b'app')
            # A normal instrumentation submission still requires a test hash.
            instance.active=None
            with self.assertRaises(ValueError):instance.start_test(digest,None)

    def test_app_only_job_uses_shared_lifecycle_and_retains_artifacts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            instance=runner.Runner(dict(sdk=str(root/'sdk'),inbox=str(root/'inbox'),state=str(root/'jobs'),avd='android_tests'))
            jid='d'*32
            (root/'jobs'/jid).mkdir()
            instance.jobs[jid]=dict(kind='recovery',status='running',phase='starting',
                                    app_sha256='a'*64,test_sha256=None,timeout_seconds=42)
            instance.cancelled=threading.Event();instance.active=jid;instance.save(jid)
            commands=[]
            def command(args,*unused):
                commands.append(args)
                if 'application-id' in args:return runner.PACKAGE
                if args[-3:]==['emu','avd','name']:return 'android_tests\nOK'
                if args[-1]=='devices':return 'emulator-5580 device'
                if 'sys.boot_completed' in args:return '1'
                return 'Success'
            def binary(args,**kwargs):
                if args[-3:]==['emu','avd','name']:output=b'android_tests\nOK'
                elif args[-3:]==['exec-out','screencap','-p']:output=b'\x89PNG\r\n\x1a\nframe'
                else:output=b'Success'
                return subprocess.CompletedProcess(args,0,stdout=output,stderr=b'')
            def recovery(device,case,check,note,progress,capture):
                results=[]
                for name in ['saved_records','edited_records','deleted_records']:
                    check();capture(name)
                    result=dict(case=name,status='passed',before_pid=10,after_pid=11)
                    progress(result);results.append(result)
                return results
            lock=Mock()
            with patch.object(runner,'run_command',side_effect=command), patch.object(runner.subprocess,'run',side_effect=binary), patch('recovery.run_recovery',side_effect=recovery):
                instance.run(jid,None,lock)
            status=instance.get_status(jid)
            self.assertEqual(status['status'],'passed',status)
            self.assertEqual(status['test_count'],3)
            self.assertIn('cleanup',status['timings'])
            self.assertEqual(len(instance.list_artifacts(jid)['artifacts']),3)
            self.assertEqual(sum(args[-4:]==['shell','pm','clear',runner.PACKAGE] for args in commands),1)
            self.assertEqual([args[-1] for args in commands if 'grant' in args],['android.permission.READ_CONTACTS'])
            self.assertFalse(any('instrument' in args or any(str(a).endswith('test.apk') for a in args) for args in commands))
            lock.close.assert_called_once()

    def test_recall_uses_public_wire_ids_observed_in_the_normal_app(self):
        from recovery import RecoveryJourney
        for kind,wire in [('EVENT','event'),('FACT','fact'),('FOLLOW_UP','followup')]:
            with self.subTest(kind=kind):
                journey=RecoveryJourney(Mock())
                journey.home=Mock();journey.write=Mock()
                expected='recall_'+wire+'_1'
                journey.ui.inspect=Mock(return_value=dict(elements=[dict(id=expected)]))
                with patch('recovery.time.monotonic',side_effect=[0,11]):
                    self.assertEqual(journey.search(kind),expected)

    def test_missing_and_duplicated_records_are_failures(self):
        from recovery import unique_record
        for ids in [[],['recall_EVENT_2'],['recall_EVENT_1','recall_EVENT_2']]:
            with self.assertRaisesRegex(AssertionError,'Missing or duplicated'):
                unique_record([dict(id=id) for id in ids],'recall_EVENT_','recall_EVENT_1')
        self.assertEqual(unique_record([dict(id='recall_EVENT_1')],'recall_EVENT_','recall_EVENT_1'),'recall_EVENT_1')

    def test_force_stop_must_really_kill_and_relaunch_with_new_pid(self):
        from recovery import RecoveryJourney
        for pids in [('10','', '11'),('10','10'),('10','', '10'),('','', '11')]:
            calls=[];values=iter(pids)
            def device(*args):
                calls.append(args)
                return next(values) if args[0]=='shell' and args[1].startswith('pidof ') else 'Success'
            journey=RecoveryJourney(device)
            journey.wait=Mock()
            if pids==('10','','11'):
                self.assertEqual(journey.restart(),dict(before_pid=10,after_pid=11))
                self.assertIn(('shell','am','force-stop',runner.PACKAGE),calls)
                self.assertFalse(any('clear' in c or 'instrument' in c for c in calls))
            else:
                with self.assertRaises(AssertionError):journey.restart()

    def test_all_cases_share_one_database_and_verify_after_each_restart(self):
        from recovery import run_recovery
        fake=Mock()
        fake.ids={'EVENT':'recall_EVENT_1'}
        fake.restart.return_value=dict(before_pid=10,after_pid=11)
        with patch('recovery.RecoveryJourney',return_value=fake):
            results=run_recovery(Mock())
        self.assertEqual([r['case'] for r in results],['saved_records','edited_records','deleted_records'])
        fake.create.assert_called_once()
        self.assertEqual(fake.restart.call_count,3)
        fake.edit.assert_called_once();fake.delete.assert_called_once();fake.verify_deleted.assert_called_once()
        self.assertEqual(fake.verify.call_count,2)


if __name__ == '__main__':
    unittest.main()
