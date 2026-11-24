"""App-only staging retains the exact release bundle's recorded provenance."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('stage', Path(__file__).resolve().parents[2]/'scripts/stage-mcp-apks.py')
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)


class StageReleaseTests(unittest.TestCase):
    def test_exact_apk_and_original_provenance_without_test_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);bundle=root/'bundle';bundle.mkdir()
            apk=bundle/'prm-0.1.0.apk';apk.write_bytes(b'signed release')
            digest=hashlib.sha256(apk.read_bytes()).hexdigest()
            original=dict(version='0.1.0',version_code=2,application_id='com.redloon.prm',
                          app_sha256=digest,apk=apk.name,certificate_sha256='c'*64,
                          source_sha256='a'*64,git_revision='b'*40)
            (bundle/'manifest.json').write_text(json.dumps(original))
            (bundle/'SHA256SUMS').write_text(f'{digest}  {apk.name}\n')
            inbox=root/'inbox';inbox.mkdir();(inbox/'test.apk').write_bytes(b'stale')
            result=stage.stage_release(inbox,apk)
            self.assertEqual(result['app_sha256'],digest)
            self.assertEqual(result['provenance']['release_manifest'],original)
            self.assertEqual(result['provenance']['source_sha256'],'a'*64)
            self.assertEqual((inbox/'app.apk').read_bytes(),apk.read_bytes())
            self.assertNotIn('test_sha256',result)
            self.assertFalse((inbox/'test.apk').exists())
            apk.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'hash'):
                stage.stage_release(inbox,apk)
            self.assertEqual((inbox/'app.apk').read_bytes(),b'signed release')

    def test_bundle_without_manifest_cannot_invent_current_checkout_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'app.apk';apk.write_bytes(b'signed')
            with self.assertRaises(FileNotFoundError):
                stage.stage_release(Path(tmp)/'inbox',apk)


if __name__ == '__main__':
    unittest.main()
