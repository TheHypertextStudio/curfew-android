import base64
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS=Path(__file__).parents[1]
sys.path.insert(0,str(SCRIPTS))
import package_native_catalog as native
import screenshot_catalog as catalog
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII=')


class NativeCatalogTests(unittest.TestCase):
    def fixture(self, root):
        welcome,setup=root/'welcome',root/'setup'
        for directory in (welcome,setup):directory.mkdir()
        profiles=[]
        for device in ('phone-portrait','phone-landscape','tablet-portrait','tablet-landscape','cover-portrait','cover-landscape','inner-portrait','inner-landscape','large-text'):
            for theme in ('light','dark'):
                name=device+'-'+theme
                profiles.append(dict(name=name,theme=theme,label=device,pixels=[1,1],dp=[1,1],fontScale=1,firstViewportActions=True))
                (welcome/(name+'.png')).write_bytes(PNG)
                for scene in native.SCENES:(setup/(name+'-'+scene+'.png')).write_bytes(PNG)
        data=dict(coverageContractSha256='ui-version',restored=True,physicalDevice=False,installedApkSha256='same-apk',sourceCommit='revision',captures=profiles)
        for directory in (welcome,setup):(directory/'manifest.json').write_text(json.dumps(data))
        durable=root/'durable';durable.mkdir();(durable/'manifest.json').write_text(json.dumps(dict(sourceRevision='revision',coverageContractSha256='ui-version')))
        return welcome,setup,durable

    def test_complete_native_matrices_are_bundled_with_original_provenance(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);welcome,setup,durable=self.fixture(root)
            with patch.object(catalog,'DURABLE',durable):
                records=native.native_records(welcome,setup,root/'native')
            self.assertEqual(len(records),198)
            self.assertTrue((root/'native/welcome-capture-manifest.json').is_file())
            self.assertTrue(all((root/'native'/r['image']).is_file() for r in records))

    def test_missing_native_image_is_rejected_before_copy(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);welcome,setup,durable=self.fixture(root)
            (setup/'phone-portrait-light-review.png').unlink()
            with patch.object(catalog,'DURABLE',durable),self.assertRaises(OSError):
                native.native_records(welcome,setup,root/'native')
            self.assertFalse((root/'native').exists())

    def test_incomplete_profiles_and_mismatched_apks_are_rejected(self):
        for field,value in [('captures',[]),('installedApkSha256','different'),('restored',False),('coverageContractSha256','old-ui')]:
            with self.subTest(field=field),tempfile.TemporaryDirectory() as d:
                root=Path(d);welcome,setup,durable=self.fixture(root)
                p=setup/'manifest.json';m=json.loads(p.read_text());m[field]=value;p.write_text(json.dumps(m))
                with patch.object(catalog,'DURABLE',durable),self.assertRaises(ValueError):
                    native.native_records(welcome,setup,root/'native')
