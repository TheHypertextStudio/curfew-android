#!/usr/bin/env python3
"""Bundle complete native onboarding captures with the maintained Compose catalog."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile

import screenshot_catalog as catalog

SCENES = ('time', 'introduction', 'defer-error', 'connection', 'keyboard', 'transfer', 'test-ready', 'connected', 'permissions', 'review')


def native_records(welcome, setup, destination):
    """Validate both complete native matrices before copying any evidence."""
    manifests = [json.loads((root / 'manifest.json').read_text()) for root in (welcome, setup)]
    spec = importlib.util.spec_from_file_location('welcome_profiles', catalog.ROOT / 'scripts/capture-welcome-matrix.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    expected = {f'{row[0]}-{theme}' for row in module.PROFILES for theme in ('light', 'dark')}
    apk = manifests[0]['installedApkSha256']
    parent = json.loads((catalog.DURABLE / 'manifest.json').read_text())
    revision = parent['sourceRevision']
    if manifests[0].get('sourceCommit') != revision:
        raise ValueError('Native welcome source revision differs from the Compose catalog.')
    records = []
    for kind, root, manifest in zip(('welcome', 'setup'), (welcome, setup), manifests):
        if not manifest.get('restored') or manifest.get('physicalDevice') is not False:
            raise ValueError('Native capture must use a guarded emulator and restore its profile.')
        if manifest.get('installedApkSha256') != apk:
            raise ValueError('Native capture matrices must use the same installed APK.')
        if manifest.get('coverageContractSha256') != parent.get('coverageContractSha256'):
            raise ValueError('Native UI fingerprint differs from the Compose catalog.')
        profiles = manifest['captures']
        if len(profiles) != len(expected) or {row['name'] for row in profiles} != expected:
            raise ValueError('Native capture profile matrix is incomplete or duplicated.')
        for row in profiles:
            scenes = ('welcome',) if kind == 'welcome' else SCENES + (('transfer-actions','test-ready-actions','review-actions') if row['fontScale'] >= 1.5 else ())
            for scene in scenes:
                file = row['name'] + ('' if kind == 'welcome' else '-' + scene) + '.png'
                width, height = catalog.png_dimensions(root / file)
                if [width, height] != row['pixels']:
                    raise ValueError(f'Native image dimensions differ from its profile: {file}')
                identifier = kind + '__' + scene.replace('-', '_') + '__' + row['name'].replace('-', '_')
                records.append(dict(id=identifier, state=scene, title=('Welcome' if kind == 'welcome' else scene.replace('-', ' ').capitalize()),
                    screen='Native welcome' if kind == 'welcome' else 'Native guided setup', device=row['name'].removesuffix('-light').removesuffix('-dark'), deviceLabel=row['label'],
                    appearance=row['theme'], appearanceLabel=row['theme'].capitalize(), position='bottom' if scene.endswith('-actions') else 'top', positionLabel='Installed app · actions' if scene.endswith('-actions') else 'Installed app · viewport sample',
                    width=row['dp'][0], height=row['dp'][1], fontScale=row['fontScale'], pixelWidth=width, pixelHeight=height,
                    captureKind='native screen', status='captured', reason=None, image=f'images/{kind}-{file}', original=root/file))
            actions = root / (row['name'] + '-actions.png')
            if kind == 'welcome' and not row.get('firstViewportActions', True):
                width, height = catalog.png_dimensions(actions)
                extra = dict(records[-1], id=records[-1]['id']+'__actions', position='bottom', positionLabel='Installed app · actions', original=actions, image='images/welcome-'+actions.name, pixelWidth=width, pixelHeight=height)
                records.append(extra)
    destination.mkdir(parents=True)
    (destination/'images').mkdir()
    for record in records:
        shutil.copyfile(record.pop('original'), destination/record['image'])
    for kind, source in zip(('welcome','setup'), (welcome,setup)):
        shutil.copyfile(source/'manifest.json', destination/f'{kind}-capture-manifest.json')
    diagnostics = destination/'diagnostics'
    for log in setup.glob('*.failed-attempt.txt'):
        diagnostics.mkdir(exist_ok=True)
        shutil.copyfile(log, diagnostics/log.name)
    return records


def package(welcome, setup, target=catalog.DURABLE):
    with tempfile.TemporaryDirectory(prefix='.native-catalog-',dir=target.parent) as directory:
        staged=Path(directory)/'catalog'
        shutil.copytree(target,staged)
        native=staged/'native'
        if native.exists(): shutil.rmtree(native)
        records=native_records(welcome,setup,native)
        root_manifest=json.loads((staged/'manifest.json').read_text())
        if not root_manifest['complete']:
            raise ValueError('A complete Compose catalog is required before packaging native captures.')
        manifest=dict(schemaVersion=1,complete=True,expected=len(records),captured=len(records),distribution='plain · native emulator',
            sourceRevision=root_manifest['sourceRevision'],coverageContractSha256=root_manifest['coverageContractSha256'],installedApkSha256=json.loads((welcome/'manifest.json').read_text())['installedApkSha256'],renderer='Android instrumentation and adb screencap',captures=records,parentCatalog='../index.html')
        (native/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        (native/'index.html').write_text(catalog.catalog_html(manifest))
        root_manifest['nativeCatalog']='native/index.html'
        (staged/'manifest.json').write_text(json.dumps(root_manifest,indent=2)+'\n')
        (staged/'index.html').write_text(catalog.catalog_html(root_manifest))
        if not catalog.publish_catalog(staged,catalog.expand_matrix(catalog.load_matrix()),target):
            raise ValueError('Current full Compose matrix is required before native publication.')
    print(f'Bundled {len(records)} native captures: {target / "native/index.html"}')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--welcome',type=Path,default=catalog.ROOT/'artifacts/native-welcome')
    parser.add_argument('--setup',type=Path,default=catalog.ROOT/'artifacts/native-setup')
    args=parser.parse_args()
    package(args.welcome,args.setup)
