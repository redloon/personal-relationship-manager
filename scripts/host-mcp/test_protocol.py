"""Actual stdio MCP contract against fake SDK executables, no host required."""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


class ProtocolTests(unittest.TestCase):
    def test_interactive_session_batch_images_recording_and_cleanup(self):
        import anyio
        from mcp import ClientSession, StdioServerParameters
        from mcp.client.stdio import stdio_client
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sdk = root/'sdk'
            script = '''#!/usr/bin/env python3
import sys,time
args=sys.argv[1:]
a=args[2:] if args[:1]==['-s'] else args
if args[:2]==['manifest','application-id']:
 print('com.idontremember.personalcrm'+('.test' if args[-1].endswith('test.apk') else ''))
elif a==['devices']: print('emulator-5580 device')
elif a==['emu','avd','name']: print('android_tests')
elif a==['shell','getprop','sys.boot_completed']: print('1')
elif a==['shell','pm','list','instrumentation']: print('com.idontremember.personalcrm.test/com.idontremember.personalcrm.HiltTestRunner')
elif a==['exec-out','uiautomator','dump','/dev/tty']: print('<hierarchy rotation="0"><node text="Search" resource-id="com.idontremember.personalcrm:id/dashboard_search" class="android.widget.EditText" bounds="[0,0][100,50]" enabled="true" clickable="true" /></hierarchy>')
elif a==['shell','wm','size']: print('Physical size: 320x640')
elif a==['exec-out','screencap','-p']: sys.stdout.buffer.write(b'\\x89PNG\\r\\n\\x1a\\n\\x00\\xffbinary')
elif a[:1]==['shell'] and 'screenrecord --time-limit' in a[-1]: print('123 456')
elif a[:1]==['shell'] and '# stop-recording' in a[-1]: print('stopped')
elif a[:1]==['shell'] and a[-1].startswith('wc -c < '): print(24)
elif a[:2]==['exec-out','cat']: sys.stdout.buffer.write(b'\\x00\\x00\\x00\\x18ftypmp42'+b'0'*12)
elif 'instrument' in a:
 assert '-r' in a
 print('INSTRUMENTATION_STATUS: class=example.Test',flush=True)
 print('INSTRUMENTATION_STATUS: test=works',flush=True)
 print('INSTRUMENTATION_STATUS_CODE: 1',flush=True)
 time.sleep(.15)
 print('INSTRUMENTATION_STATUS: class=example.Test')
 print('INSTRUMENTATION_STATUS: test=works')
 print('INSTRUMENTATION_STATUS_CODE: 0')
 print('OK (1 test)')
 print('INSTRUMENTATION_CODE: -1')
else: print('Success')
'''
            for name in ['platform-tools/adb','cmdline-tools/latest/bin/apkanalyzer']:
                file=sdk/name
                file.parent.mkdir(parents=True,exist_ok=True)
                file.write_text(script)
                file.chmod(0o755)
            inbox=root/'inbox'
            inbox.mkdir()
            for name in ['app.apk','test.apk']:
                (inbox/name).write_bytes(b'fake-apk')
            digest=hashlib.sha256(b'fake-apk').hexdigest()
            config=root/'config.json'
            config.write_text(json.dumps(dict(sdk=str(sdk),inbox=str(inbox),state=str(root/'jobs'),avd='android_tests')))
            async def run():
                server=StdioServerParameters(command=sys.executable,args=[str(Path(__file__).with_name('runner.py')),str(config)])
                async with stdio_client(server) as (read,write):
                    async with ClientSession(read,write) as client:
                        await client.initialize()
                        names={t.name for t in (await client.list_tools()).tools}
                        self.assertTrue({'inspect','execute_steps','start_recording','get_artifact','start_recovery_test'} <= names)
                        async def call(name,args=None):
                            result=await client.call_tool(name,args or {})
                            self.assertFalse(result.isError, str(result.content))
                            text=next(c.text for c in result.content if c.type=='text')
                            return json.loads(text),result
                        async def terminal(jid,ready=False):
                            for _ in range(200):
                                status,_=await call('get_status',dict(job_id=jid))
                                if status['status']!='running' or ready and status['phase']=='ready': return status
                                await anyio.sleep(.05)
                            self.fail('job did not finish')
                        started,_=await call('start_session',dict(app_sha256=digest,test_sha256=digest))
                        sid=started['session_id']
                        self.assertEqual((await terminal(sid,True))['phase'],'ready')
                        inspection,result=await call('inspect',dict(session_id=sid))
                        self.assertTrue(any(c.type=='image' for c in result.content))
                        self.assertEqual(inspection['snapshot']['elements'][0]['id'],'dashboard_search')
                        invalid=await client.call_tool('execute_steps',dict(session_id=sid,steps=[dict(action='type',text='café')]))
                        self.assertTrue(invalid.isError)
                        batch,_=await call('execute_steps',dict(session_id=sid,steps=[dict(action='type',text='Lantern'),dict(action='assert',target=dict(id='dashboard_search'))]))
                        status=await terminal(batch['job_id'])
                        self.assertEqual(status['status'],'passed')
                        self.assertEqual(len(status['results']),2)
                        self.assertFalse(status['results'][0]['verified'])
                        artifact,status_image=await call('get_artifact',dict(job_id=batch['job_id'],artifact_id=status['artifact']['artifact_id']))
                        self.assertTrue(any(c.type=='image' for c in status_image.content))
                        recording,_=await call('start_recording',dict(session_id=sid))
                        video,_=await call('stop_recording',dict(session_id=sid,recording_id=recording['recording_id']))
                        self.assertEqual(video['mime_type'],'video/mp4')
                        self.assertTrue(Path(video['shared_path']).is_file())
                        await call('start_recording',dict(session_id=sid))
                        await call('end_session',dict(session_id=sid))
                        self.assertEqual((await terminal(sid))['status'],'passed')
                        health,_=await call('health')
                        self.assertFalse(health['busy'])
                        artifacts,_=await call('list_artifacts',dict(job_id=sid))
                        self.assertEqual(len(artifacts['artifacts']),4)
                        for filter_value, expected_timeout in [(None, 900), ('com.idontremember.personalcrm.Example#works', 180)]:
                            started,_=await call('start_test',dict(app_sha256=digest,test_sha256=digest,filter=filter_value))
                            status,_=await call('get_status',dict(job_id=started['job_id'],wait_seconds=5))
                            self.assertEqual(status['status'],'passed')
                            self.assertEqual(status['test_count'],1)
                            self.assertEqual(status['timeout_seconds'],expected_timeout)
                            self.assertEqual(status['tests'][0]['class_name'],'example.Test')
                            self.assertEqual(status['tests'][0]['test_name'],'works')
                            self.assertEqual(status['tests'][0]['outcome'],'passed')
                            self.assertGreater(status['tests'][0]['duration_seconds'],.05)
                            self.assertTrue({'starting','emulator','install','tests','cleanup','total'} <= set(status['timings']))
                            diagnostic,_=await call('read_logs',dict(job_id=started['job_id'],log='logcat',offset=0))
                            self.assertEqual(diagnostic['text'],'Success\n')
            anyio.run(run)


if __name__ == '__main__':
    unittest.main()
