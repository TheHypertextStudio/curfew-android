#!/usr/bin/env python3
"""Package a complete screenshot catalog for Codex's inline viewer (under 1 MB).

Run with a Python environment providing Pillow. Full-resolution captures stay in
catalog/images; the viewer connects to them through a user-selected directory.
"""
import argparse
import base64
import io
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def package(catalog, output):
    manifest = json.loads((catalog / 'manifest.json').read_text())
    if not manifest.get('complete'):
        raise ValueError('An incomplete catalog cannot be published inline')
    captures = list(manifest['captures'])
    native_path = catalog / 'native/manifest.json'
    if native_path.exists():
        native = json.loads(native_path.read_text())
        if not native.get('complete') or native['sourceRevision'] != manifest['sourceRevision'] or native.get('coverageContractSha256') != manifest['coverageContractSha256']:
            raise ValueError('Native captures must match the complete Compose catalog')
        captures += [dict(c, state='native_' + c['state'], image='native/' + c['image']) for c in native['captures']]
    payload = dict(catalogManifestSha256=hashlib.sha256((catalog/"manifest.json").read_bytes()).hexdigest(),nativeManifestSha256=hashlib.sha256(native_path.read_bytes()).hexdigest() if native_path.exists() else None, fingerprint=manifest["coverageContractSha256"], states={}, devices={}, captures=[], embedded={})
    for c in captures:
        payload['states'][c['state']] = [c['title'], c['screen']]
        device = c['device'].replace('-', '_')
        payload['devices'][device] = [c['deviceLabel'], c['width'], c['height']]
        payload['captures'].append([c['state'], device, c['appearance'], c['positionLabel'], c['image']])
    template = (ROOT / 'scripts/templates/inline_catalog.html').read_text()
    def fragment():
        return template.replace('__CATALOG_DATA__', json.dumps(payload, separators=(',', ':'), ensure_ascii=False).replace('<', '\\u003c'))
    # First-page previews and onboarding on phones get priority. Every capture
    # remains indexed; selected local PNG files take precedence over previews.
    first_page = {c['image'] for theme in ('light', 'dark') for c in [row for row in captures if row['appearance'] == theme][:12]}
    def priority(c):
        return (-1 if c['image'] in first_page else 0 if c['state'] == 'welcome' else 1 if c['device'] == 'phone_portrait' and c['screen'] in ('Wake time', 'Check-in', 'Alarm readiness', 'Review') else 2 if c['device'] == 'phone_portrait' else 3)
    for c in sorted(captures, key=priority):
        path = catalog / c['image']
        with Image.open(path) as image:
            image.thumbnail((540, 850))
            buffer = io.BytesIO()
            image.convert('RGB').save(buffer, format='WEBP', quality=76)
        payload['embedded'][c['image']] = 'data:image/webp;base64,' + base64.b64encode(buffer.getvalue()).decode()
        if len(fragment().encode()) > 980_000:
            del payload['embedded'][c['image']]
            break
    result = fragment()
    if len(result.encode()) >= 1_000_000:
        raise ValueError('Catalog index exceeds inline payload limit')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(result)
    return len(captures), len(payload['embedded']), len(result.encode())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, default=ROOT / 'artifacts/screenshot-catalog')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print('Indexed %s captures; embedded %s previews; %s bytes.' % package(args.catalog, args.output))
