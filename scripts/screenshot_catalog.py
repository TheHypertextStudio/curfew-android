#!/usr/bin/env python3
"""Render Curfew's production Compose screens and build a portable screenshot catalog."""
import argparse
import html
import hashlib
import itertools
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
PREVIEWS = ROOT / "app/src/screenshotTest/kotlin/studio/hypertext/curfew/screenshots/CatalogPreviews.kt"
OUTPUT = ROOT / "app/build/reports/screenshot-catalog"
RAW = ROOT / "app/build/screenshot-catalog/raw"
DURABLE = ROOT / "artifacts/screenshot-catalog"
POSITION_LABELS = {
    "top": "Screen content · top", "bottom": "Screen content · end",
    "campaign": "Wake campaign · top", "campaign_end": "Wake campaign · end",
    "readiness": "Readiness section · top", "readiness_end": "Readiness section · end", "condition": "Wake condition · top",
    "condition_end": "Wake condition · end", "account": "Account section · top",
    "account_end": "Account section · end",
}


def load_matrix():
    return json.loads((ROOT / "config/screenshots/matrix.json").read_text())


def expand_matrix(matrix):
    cases = []
    for state, device, appearance in itertools.product(matrix["states"], matrix["devices"], matrix["appearances"]):
        for position in state["positions"]:
            identifier = "__".join((state["id"], position, device["id"], appearance["id"]))
            if not re.fullmatch(r"[a-z][a-z0-9_]+", identifier):
                raise ValueError(f"Invalid matrix identifier: {identifier}")
            cases.append(dict(
                id=identifier, function=f"catalog__{identifier}", state=state["id"],
                title=state["title"], screen=state["screen"], position=position,
                positionLabel=POSITION_LABELS[position], captureKind="screen content",
                device=device["id"], deviceLabel=device["label"], width=device["width"],
                height=device["height"], appearance=appearance["id"],
                appearanceLabel=appearance["label"], dark=appearance["dark"],
                fontScale=device.get("fontScale", appearance["fontScale"]),
            ))
    if len({c["id"] for c in cases}) != len(cases):
        raise ValueError("Duplicate matrix case identifiers")
    return cases


def audit_coverage(matrix, root=ROOT):
    """Require source review when UI routes, branches, models or fixture inventory change."""
    contract = json.loads((root / "config/screenshots/coverage.json").read_text())
    ui = root / "app/src/main/java/studio/hypertext/curfew/ui"
    actual = {str(path.relative_to(root)) for path in ui.rglob("*.kt")}
    for source_set in ("main", "plain", "gms"):
        for source in (root / f"app/src/{source_set}").rglob("*.kt"):
            if re.search(r"@Composable\b|\bsetContent\s*\{|\bclass\s+\w+(?:\([^{}]*\))?\s*:\s*(?:ComponentActivity|Activity)\b", source.read_text()):
                actual.add(str(source.relative_to(root)))
    actual.update([
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/values/strings.xml",
        "app/src/main/java/studio/hypertext/curfew/MainActivity.kt",
        "app/src/main/java/studio/hypertext/curfew/readiness/AlarmReadinessEvaluator.kt",
        "app/src/main/java/studio/hypertext/curfew/account/AccountRecoveryCoordinator.kt",
        "app/src/screenshotTest/kotlin/studio/hypertext/curfew/screenshots/CatalogFixtures.kt",
    ])
    for directory in (root / "app/src/main/res").glob("layout*"):
        actual.update(str(source.relative_to(root)) for source in directory.rglob("*.xml"))
    reviewed = contract["reviewedSources"]
    issues = []
    for path in sorted(actual | set(reviewed)):
        source = root / path
        if path not in reviewed:
            issues.append(f"New UI source needs coverage review: {path}")
        elif path not in actual or not source.is_file():
            issues.append(f"Removed UI source needs coverage review: {path}")
        elif hashlib.sha256(source.read_bytes()).hexdigest() != reviewed[path]:
            issues.append(f"Changed UI source needs coverage review: {path}")
    expected = set(contract["requiredStates"])
    states = {state["id"] for state in matrix["states"]}
    if expected != states:
        issues.append("State inventory differs from the reviewed coverage contract")
    return issues


def refresh_catalog_viewer(output):
    """Update navigation and maintenance instructions without changing screenshot pixels."""
    manifest = json.loads((output / "manifest.json").read_text())
    expected = {case["id"] for case in expand_matrix(load_matrix())}
    contract_hash = hashlib.sha256((ROOT / "config/screenshots/coverage.json").read_bytes()).hexdigest()
    if manifest.get("coverageContractSha256") != contract_hash:
        raise ValueError("UI coverage changed since capture; render the full matrix before refreshing the viewer.")
    if not manifest["complete"] or {capture["id"] for capture in manifest["captures"]} != expected:
        raise ValueError("A successful render of the current full matrix is required before refreshing the maintained viewer.")
    for capture in manifest["captures"]:
        png_dimensions(output / capture["image"])
    (output / "index.html").write_text(catalog_html(manifest))
    shutil.copyfile(ROOT / "config/screenshots/coverage.json", output / "coverage.json")
    shutil.copyfile(ROOT / "docs/SCREENSHOT_CATALOG.md", output / "README.md")


def publish_catalog(source, cases, target=DURABLE):
    """Replace the maintained artifact only with a successful, complete matrix."""
    manifest = json.loads((source / "manifest.json").read_text())
    expected = {case["id"] for case in cases}
    if not manifest["complete"] or {case["id"] for case in manifest["captures"]} != expected:
        return False
    target.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".screenshot-catalog-", dir=target.parent))
    previous = target.with_name(target.name + ".previous")
    try:
        shutil.copytree(source, staging, dirs_exist_ok=True)
        if previous.exists():
            shutil.rmtree(previous)
        if target.exists():
            target.rename(previous)
        try:
            staging.rename(target)
        except OSError:
            if previous.exists() and not target.exists():
                previous.rename(target)
            raise
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    return True


def reusable_captures(cases, source=DURABLE, distribution="plain"):
    """Reuse only captures whose UI fingerprint and complete case inputs still match."""
    manifest = json.loads((source / "manifest.json").read_text())
    contract_hash = hashlib.sha256((ROOT / "config/screenshots/coverage.json").read_bytes()).hexdigest()
    if not manifest.get("complete") or manifest.get("coverageContractSha256") != contract_hash:
        raise ValueError("Resume requires a complete catalog with the current reviewed UI fingerprint.")
    if manifest.get("distribution") != distribution:
        raise ValueError("Resume requires the same distribution; render the requested variant in full.")
    current = {case["id"]: case for case in cases}
    retained = []
    for capture in manifest["captures"]:
        case = current.get(capture["id"])
        if case and all(capture.get(key) == value for key, value in case.items()):
            png_dimensions(source / capture["image"])
            retained.append((case, source / capture["image"]))
    return retained


def render_batches(cases, size=150):
    if size < 1 or size > 300:
        raise ValueError("Screenshot batch size must be between 1 and 300")
    return [cases[index:index + size] for index in range(0, len(cases), size)]


def refresh_maintained_viewer(cases, output=OUTPUT, target=DURABLE):
    """Refresh a verified full artifact even after clean or a partial render."""
    target.parent.mkdir(parents=True, exist_ok=True)
    issues = []
    for candidate in (target, output, target.with_name(target.name + ".previous")):
        if not candidate.is_dir():
            continue
        with tempfile.TemporaryDirectory(prefix=".viewer-", dir=target.parent) as directory:
            staged = Path(directory) / "catalog"
            try:
                shutil.copytree(candidate, staged)
                refresh_catalog_viewer(staged)
            except (OSError, ValueError, KeyError) as error:
                issues.append(str(error))
                continue
            if publish_catalog(staged, cases, target):
                return
    raise ValueError("No complete matching catalog is available to refresh. Render the full matrix. " + "; ".join(issues))


def preview_source(cases):
    source = [
        "// Generated by scripts/screenshot_catalog.py. Edit matrix.json and CatalogFixtures.kt.",
        "package studio.hypertext.curfew.screenshots", "",
        "import androidx.compose.runtime.Composable",
        "import androidx.compose.ui.tooling.preview.Preview",
        "import com.android.tools.screenshot.PreviewTest", "",
    ]
    for c in cases:
        source += [
            "@PreviewTest",
            f'@Preview(name = "{c["id"]}", widthDp = {c["width"]}, heightDp = {c["height"]}, '
            f'fontScale = {c["fontScale"]}f, locale = "en", showBackground = true)',
            "@Composable",
            f'fun {c["function"]}() {{',
            f'    CatalogFixture(CatalogState.{c["state"].upper()}, "{c["position"]}", {str(c["dark"]).lower()})',
            "}", "",
        ]
    return "\n".join(source)


def generate(cases):
    PREVIEWS.parent.mkdir(parents=True, exist_ok=True)
    source = preview_source(cases)
    if not PREVIEWS.exists() or PREVIEWS.read_text() != source:
        PREVIEWS.write_text(source)


def png_dimensions(path):
    with path.open("rb") as image:
        header = image.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError("Invalid PNG")
    width, height = struct.unpack(">II", header[16:24])
    if not width or not height:
        raise ValueError("Empty image")
    return width, height


def build_catalog(cases, renders, output, renderer_ok=True, distribution="plain"):
    output.mkdir(parents=True, exist_ok=True)
    images = output / "images"
    if images.exists():
        shutil.rmtree(images)
    images.mkdir()
    # Official renderer uses both directory and filename forms of the function name.
    pngs = list(renders.rglob("*.png")) if renders.exists() else []
    records = []
    for case in cases:
        record = dict(case, image=None, status="missing", reason="Missing capture")
        pattern = re.compile(r"(?:^|[./])" + re.escape(case["function"]) + r"(?=[_./]|$)")
        matches = [p for p in pngs if pattern.search(p.relative_to(renders).as_posix())]
        if len(matches) > 1:
            record.update(status="ambiguous", reason="Multiple captures matched this case")
        elif matches:
            try:
                width, height = png_dimensions(matches[0])
                destination = images / f'{case["id"]}.png'
                shutil.copyfile(matches[0], destination)
                record.update(image=f"images/{destination.name}", status="captured", reason=None,
                              pixelWidth=width, pixelHeight=height)
            except (OSError, ValueError) as error:
                record.update(status="invalid", reason=str(error))
        records.append(record)
    captured = sum(r["status"] == "captured" for r in records)
    manifest = dict(schemaVersion=1, generatedAt=datetime.now(timezone.utc).isoformat(),
                    coverageContractSha256=hashlib.sha256((ROOT / "config/screenshots/coverage.json").read_bytes()).hexdigest(), renderer="Android Compose Preview Screenshot Testing",
                    sourceRevision=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), sourcePullRequest=load_matrix().get("sourcePullRequest"),
                    distribution=distribution, rendererSucceeded=renderer_ok,
                    expected=len(records), captured=captured, missing=len(records) - captured,
                    complete=renderer_ok and captured == len(records), captures=records,
                    boundaries=["App-owned Compose UI with synthetic data", "Fixed window dimensions, no hinge simulation",
                                "No OS permission dialogs, notifications, keyboard, browser authentication or alarm delivery proof"])
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (output / "index.html").write_text(catalog_html(manifest))
    return manifest["complete"]


def catalog_html(manifest):
    esc = lambda value: html.escape(str(value), quote=True)
    records = manifest["captures"]
    renderer = html.escape(manifest.get("renderer", "Android Compose Preview Screenshot Testing"))
    capture_note = "Installed emulator screenshots with synthetic data; system bars and keyboard are included where captured. Physical hinge behavior, external sign-in, provider connectivity and alarm delivery require separate checks." if manifest.get("parentCatalog") else "App UI previews with synthetic data. Foldables represent window sizes; system dialogs, keyboards, hinge behavior, external sign-in, and alarm delivery require runtime checks."
    controls = []
    for field, label, display in [("screen", "Screen", "screen"), ("state", "State", "title"),
                                   ("device", "Form factor", "deviceLabel"),
                                   ("appearance", "Appearance", "appearanceLabel"),
                                   ("position", "View", "positionLabel")]:
        values = dict((r[field], r[display]) for r in records)
        options = ''.join(f'<option value="{esc(v)}">{esc(t)}</option>' for v, t in values.items())
        all_option = "" if field == "appearance" else '<option value="">All</option>'
        controls.append(f'<label>{label}<select data-filter="{field}">{all_option}{options}</select></label>')
    sections = []
    for screen in dict.fromkeys(r["screen"] for r in records):
        figures = []
        for r in (r for r in records if r["screen"] == screen):
            attrs = ' '.join(f'data-{field}="{esc(r[field])}"' for field in ("screen", "state", "device", "appearance", "position"))
            alt = f'{r["title"]}, {r["deviceLabel"]}, {r["appearanceLabel"]}, {r["positionLabel"]}'
            visual = (f'<a class="capture" href="{esc(r["image"])}" target="_blank" rel="noopener" aria-label="{esc(alt)} — open full size">'
                      f'<img src="{esc(r["image"])}" alt="{esc(alt)}" loading="lazy" width="{r["pixelWidth"]}" height="{r["pixelHeight"]}"></a>'
                      if r["image"] else f'<div class="missing">{esc(r["reason"])}</div>')
            figures.append(f'<figure {attrs} data-search="{esc(alt.lower())}">{visual}<figcaption>'
                           f'<h3>{esc(r["title"])}</h3><p>{esc(r["deviceLabel"])} · {r["width"]} × {r["height"]} dp</p>'
                           f'<p>{esc(r["appearanceLabel"])} · {esc(r["positionLabel"])}</p></figcaption></figure>')
        sections.append(f'<section class="screen"><h2>{esc(screen)}</h2><div class="captures">{"".join(figures)}</div></section>')
    status = "Complete" if manifest["complete"] else "Incomplete"
    failure = f'<p role="alert">Generation stopped: {esc(manifest["failure"])}</p>' if manifest.get("failure") else ""
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex"><title>Curfew Android · Screenshot catalog</title>
<style>
:root{{color-scheme:light dark;--paper:#f5f2eb;--ink:#24221f;--muted:#686157;--line:#d6cfc3;--accent:#795b17;--panel:#fffdf8}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--paper);color:var(--ink);font:15px/1.5 system-ui,sans-serif}}
header,main,footer{{max-width:1680px;margin:auto;padding:32px clamp(20px,4vw,64px)}}header{{padding-bottom:20px;border-bottom:1px solid var(--line)}}
.eyebrow{{font-size:12px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--accent)}}
h1{{font-size:clamp(28px,4vw,48px);line-height:1.12;margin:12px 0}}h2{{font-size:23px;margin:38px 0 18px}}h3{{font-size:15px;margin:0 0 6px}}p{{margin:6px 0}}
.intro{{max-width:76ch;color:var(--muted)}}.summary{{margin-top:20px;font-weight:600}}a{{color:var(--accent)}}
.filters{{display:flex;flex-wrap:wrap;gap:12px;margin:0 0 16px}}label{{display:grid;gap:5px;font-size:12px;font-weight:600;flex:1;min-width:145px}}
input,select{{width:100%;min-height:44px;background:var(--panel);color:var(--ink);border:1px solid var(--line);border-radius:4px;padding:8px;font:inherit;font-size:14px}}
:focus-visible{{outline:3px solid var(--accent);outline-offset:3px}}.captures{{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,290px),1fr));gap:28px 20px}}
figure{{margin:0;min-width:0}}.capture,.missing{{display:flex;align-items:flex-start;justify-content:center;background:var(--panel);border:1px solid var(--line);border-radius:4px}}
.capture img{{display:block;width:100%;height:auto;object-fit:contain}}.missing{{min-height:160px;align-items:center;color:var(--muted);padding:24px;border-style:dashed}}
figcaption{{padding:12px 0;color:var(--muted);font-size:13px}}figcaption h3{{color:var(--ink)}}[hidden]{{display:none!important}}
footer{{color:var(--muted);border-top:1px solid var(--line)}}#empty{{padding:40px 0}}#count{{color:var(--muted)}}
@media(prefers-color-scheme:dark){{:root{{--paper:#1d1b18;--ink:#f0ebe2;--muted:#bdb4a7;--line:#494237;--accent:#e9c76f;--panel:#28241f}}}}
@media(max-width:540px){{header,main,footer{{padding:24px 20px}}}}
button{{min-height:44px;border:1px solid var(--line);border-radius:4px;background:var(--panel);color:var(--ink);padding:8px 16px;font:inherit;cursor:pointer}}button:disabled{{opacity:.45;cursor:default}}
.catalog-actions{{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:16px 0}}.catalog-actions p{{margin:0}}.pagination{{display:flex;align-items:center;justify-content:center;gap:12px;margin:24px 0}}
#presentation{{width:100vw;height:100dvh;max-width:none;max-height:none;margin:0;padding:16px;border:0;background:var(--paper);color:var(--ink);overflow:hidden}}
#presentation[open]{{display:flex;flex-direction:column;gap:12px}}.presentation-toolbar{{display:flex;align-items:center;gap:8px;flex-wrap:wrap;flex:none}}#slide-count{{margin-right:auto;color:var(--muted)}}
#slide-image{{min-height:0;min-width:0;flex:1;width:100%;height:100%;object-fit:contain}}#slide-caption{{flex:none;margin:0;text-align:center;font-size:14px}}.presentation-help{{font-size:12px;color:var(--muted);margin:0;text-align:center}}
body:has(#presentation[open]){{overflow:hidden}}
</style></head><body><header><div class="eyebrow">Curfew / Android</div><h1>Screenshot catalog</h1>
<p class="intro">Curfew Sunrise · PR #18. Production screen content, deterministic states, and the windows they live in. Select a capture to inspect its original resolution.</p>
<p class="summary">{status} · {manifest["captured"]} of {manifest["expected"]} captures · {esc(manifest["distribution"])} distribution</p>{failure}
<p class="intro">{capture_note}</p>
<p>{'<a href="../index.html">All screen states</a> · ' if manifest.get("parentCatalog") else ""}{'<a href="native/index.html">Native onboarding and setup</a> · ' if manifest.get("nativeCatalog") else ""}<a href="manifest.json">Coverage manifest</a> · Source: {esc(manifest.get("sourceRevision", "unknown"))}</p></header><main id="main">
<div class="filters"><label>Search<input id="search" type="search" placeholder="Find a state or device"></label>{''.join(controls)}</div>
<div class="catalog-actions"><p id="count" role="status" aria-live="polite">{len(records)} captures shown</p><button id="present" type="button">Present screenshots</button></div><p id="empty" hidden>No captures match these filters.</p>
<nav class="pagination" aria-label="Preview pages"><button type="button" class="page-previous">← Previous page</button><span class="page-label" aria-live="polite"></span><button type="button" class="page-next">Next page →</button></nav>
<noscript><p>All captures are listed below. Enable JavaScript to filter them.</p></noscript>{''.join(sections)}
<nav class="pagination" aria-label="Preview pages, bottom"><button type="button" class="page-previous">← Previous page</button><span class="page-label"></span><button type="button" class="page-next">Next page →</button></nav></main>
<footer>Rendered by {renderer}. Catalog generation does not approve regression baselines. Screen-content previews and native system screenshots retain their own source and capture metadata.</footer>
<dialog id="presentation" aria-label="Screenshot presentation" aria-describedby="presentation-help">
<div class="presentation-toolbar"><span id="slide-count" role="status" aria-live="polite"></span><button id="previous-slide" type="button" aria-label="Previous screenshot">← Previous</button><button id="next-slide" type="button" aria-label="Next screenshot">Next →</button><button id="close-presentation" type="button">Close</button></div>
<img id="slide-image" alt=""><p id="slide-caption"></p><p class="presentation-help" id="presentation-help">Tab / → next · Shift+Tab / ← previous · Escape close</p>
</dialog>
<script>
const figures=[...document.querySelectorAll('figure')], filters=[...document.querySelectorAll('[data-filter]')], search=document.querySelector('#search');
let pageIndex=0;
const pageSize=12;
function matchesFilters(figure){{return figure.dataset.search.includes(search.value.trim().toLowerCase())&&filters.every(filter=>!filter.value||figure.dataset[filter.dataset.filter]===filter.value);}}
function update(reset=true){{if(reset)pageIndex=0;const matches=figures.filter(matchesFilters),pages=Math.max(1,Math.ceil(matches.length/pageSize));pageIndex=Math.min(pageIndex,pages-1);const visible=new Set(matches.slice(pageIndex*pageSize,(pageIndex+1)*pageSize));for(const figure of figures)figure.hidden=!visible.has(figure);for(const section of document.querySelectorAll('.screen'))section.hidden=![...section.querySelectorAll('figure')].some(figure=>!figure.hidden);document.querySelector('#count').textContent=matches.length?`${{matches.length}} matching captures · ${{pageIndex*pageSize+1}}–${{Math.min((pageIndex+1)*pageSize,matches.length)}} shown`:'0 matching captures';document.querySelector('#empty').hidden=matches.length!==0;document.querySelector('#present').disabled=!matches.some(figure=>figure.querySelector('img'));document.querySelectorAll('.page-label').forEach(label=>label.textContent=`${{pageIndex+1}} / ${{pages}}`);document.querySelectorAll('.page-previous').forEach(button=>button.disabled=pageIndex===0);document.querySelectorAll('.page-next').forEach(button=>button.disabled=pageIndex===pages-1);}}
filters.forEach(filter=>filter.addEventListener('change',()=>update()));search.addEventListener('input',()=>update());
document.querySelectorAll('.page-previous').forEach(button=>button.addEventListener('click',()=>{{pageIndex--;update(false);document.querySelector('#main').scrollIntoView();}}));
document.querySelectorAll('.page-next').forEach(button=>button.addEventListener('click',()=>{{pageIndex++;update(false);document.querySelector('#main').scrollIntoView();}}));
const presentation=document.querySelector('#presentation'), slideImage=document.querySelector('#slide-image');
let slides=[], slideIndex=0;
function showSlide(index){{slideIndex=Math.max(0,Math.min(index,slides.length-1));const figure=slides[slideIndex],image=figure.querySelector('img');slideImage.src=image.src;slideImage.alt=image.alt;document.querySelector('#slide-caption').textContent=[...figure.querySelectorAll('figcaption h3,figcaption p')].map(node=>node.textContent).join(' · ');document.querySelector('#slide-count').textContent=`${{slideIndex+1}} / ${{slides.length}}`;document.querySelector('#previous-slide').disabled=slideIndex===0;document.querySelector('#next-slide').disabled=slideIndex===slides.length-1;}}
document.querySelector('#present').addEventListener('click',()=>{{slides=figures.filter(figure=>matchesFilters(figure)&&figure.querySelector('img'));if(!slides.length)return;showSlide(0);presentation.showModal();}});
document.querySelector('#previous-slide').addEventListener('click',()=>showSlide(slideIndex-1));
document.querySelector('#next-slide').addEventListener('click',()=>showSlide(slideIndex+1));
document.querySelector('#close-presentation').addEventListener('click',()=>presentation.close());
presentation.addEventListener('keydown',event=>{{if(event.key==='Tab'||event.key==='ArrowRight'||event.key==='ArrowLeft'){{event.preventDefault();showSlide(slideIndex+((event.key==='ArrowLeft'||(event.key==='Tab'&&event.shiftKey))?-1:1));}}}});
presentation.addEventListener('close',()=>document.querySelector('#present').focus());
update();
</script></body></html>'''


def write_failure_report(message, output=OUTPUT):
    """Early validation failures must not leave the current attempt labeled successful."""
    output.mkdir(parents=True, exist_ok=True)
    manifest = dict(schemaVersion=1, complete=False, rendererSucceeded=False,
                    expected=0, captured=0, missing=0, distribution="not rendered",
                    captures=[], failure=message)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (output / "index.html").write_text(catalog_html(manifest))
    (output / "render.log").write_text(message + "\n")


def reference_snapshot():
    """Detect any accidental writes to ordinary regression references by the alpha plugin."""
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for directory in (ROOT / "app/src").glob("screenshotTest*/reference")
            for path in directory.rglob("*.png")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh-viewer", action="store_true", help="Refresh the maintained HTML from the existing complete matrix without rendering")
    parser.add_argument("--resume", action="store_true", help="Reuse matching successful captures when adding matrix cases; changed UI requires a full render")
    parser.add_argument("--check-coverage", action="store_true", help="Check that UI sources and states match the reviewed coverage contract")
    parser.add_argument("--generate-only", action="store_true", help="Generate Android Studio/Gradle previews without rendering")
    parser.add_argument("--state", action="append", help="Render only this fixture ID (repeatable)")
    parser.add_argument("--device", action="append", help="Render only this form-factor ID (repeatable)")
    parser.add_argument("--appearance", action="append", help="Render only this appearance ID (repeatable)")
    parser.add_argument("--batch-size", type=int, default=150, help="Maximum captures per fresh renderer JVM (1–300, default 150)")
    parser.add_argument("--distribution", choices=["plain", "gms"], default="plain")
    args = parser.parse_args()
    try:
        matrix = load_matrix()
        issues = audit_coverage(matrix)
        if issues:
            raise ValueError("\n".join(issues) + "\nReview the UI/fixtures and update config/screenshots/coverage.json.")
        cases = expand_matrix(matrix)
    except (OSError, ValueError, KeyError) as error:
        write_failure_report(str(error))
        raise ValueError(str(error)) from error
    if args.check_coverage:
        print("Coverage contract matches current UI sources and state inventory.")
        return 0
    if args.refresh_viewer:
        refresh_maintained_viewer(cases)
        print(f"Maintained viewer refreshed: {DURABLE / 'index.html'}")
        return 0
    generate(cases)
    if args.generate_only:
        print(f"Generated {len(cases)} official Compose screenshot previews.")
        return 0
    selected = [c for c in cases if all(not getattr(args, field) or c[field] in getattr(args, field)
                                      for field in ("state", "device", "appearance"))]
    if not selected:
        parser.error("No captures match the requested state/device/appearance. See config/screenshots/matrix.json.")
    retained = reusable_captures(selected, distribution=args.distribution) if args.resume else []
    if retained:
        provenance = OUTPUT / "reused-provenance"
        provenance.mkdir(parents=True, exist_ok=True)
        for filename in ("manifest.json", "render.log", "coverage.json"):
            if (DURABLE / filename).is_file():
                shutil.copyfile(DURABLE / filename, provenance / filename)
    # Never reuse a previous run's PNGs or present a stale complete catalog after a failed build.
    if RAW.exists():
        shutil.rmtree(RAW)
    for case, image in retained:
        destination = RAW / "verified-resume" / f'{case["function"]}_resumed_0.png'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(image, destination)
    retained_ids = {case["id"] for case, _ in retained}
    pending = [case for case in selected if case["id"] not in retained_ids]
    build_catalog(selected, RAW, OUTPUT, renderer_ok=False, distribution=args.distribution)
    environment = os.environ.copy()
    sdk = Path.home() / "Library/Android/sdk"
    if not environment.get("ANDROID_HOME") and sdk.is_dir():
        environment["ANDROID_HOME"] = str(sdk)
    task = f':app:update{args.distribution.capitalize()}DebugScreenshotTest'
    batches = render_batches(pending, args.batch_size)
    references_before = reference_snapshot()
    coverage_before = hashlib.sha256((ROOT / "config/screenshots/coverage.json").read_bytes()).hexdigest()
    matrix_before = (ROOT / "config/screenshots/matrix.json").read_bytes()
    print(f"Rendering {len(pending)} captures with Android Compose Preview Screenshot Testing; reusing {len(retained)} matching verified captures…", flush=True)
    log = OUTPUT / "render.log"
    renderer_ok = True
    with log.open("w") as combined:
        for index, batch in enumerate(batches, 1):
            batch_id = f"{index:04d}"
            command = [str(ROOT / "gradlew"), task, "--rerun", "-PcurfewScreenshotCatalog=true",
                       f"-PcurfewScreenshotCatalogBatch={batch_id}", "--console=plain"]
            for case in batch:
                command += ["--tests", f'*{case["function"]}*']
            print(f"Batch {index}/{len(batches)} · {len(batch)} captures", flush=True)
            batch_log = OUTPUT / f"render-batch-{batch_id}.log"
            with batch_log.open("w") as stream:
                result = subprocess.run(command, cwd=ROOT, env=environment,
                                        stdout=stream, stderr=subprocess.STDOUT)
            combined.write(f"\nBatch {index}/{len(batches)}\n")
            combined.write(batch_log.read_text())
            combined.flush()
            if result.returncode:
                renderer_ok = False
                break
    references_unchanged = reference_snapshot() == references_before
    coverage_unchanged = (
        hashlib.sha256((ROOT / "config/screenshots/coverage.json").read_bytes()).hexdigest() == coverage_before
        and (ROOT / "config/screenshots/matrix.json").read_bytes() == matrix_before
        and not audit_coverage(load_matrix())
    )
    if not coverage_unchanged:
        print("UI sources or matrix changed during capture. Run the catalog again from stable sources.", file=sys.stderr)
    complete = build_catalog(selected, RAW, OUTPUT,
                             renderer_ok=renderer_ok and references_unchanged and coverage_unchanged,
                             distribution=args.distribution)
    if not references_unchanged:
        print("Regression reference files unexpectedly changed. Inspect app/src/screenshotTest*/reference.", file=sys.stderr)
    print(f"Catalog: {OUTPUT / 'index.html'}")
    print(f"Renderer log: {log}")
    if not complete:
        print("Catalog incomplete. Inspect render.log and manifest.json; no previous captures were reused.", file=sys.stderr)
        print("\n".join(log.read_text().splitlines()[-35:]), file=sys.stderr)
        return 1
    if len(selected) == len(cases):
        refresh_catalog_viewer(OUTPUT)
    if publish_catalog(OUTPUT, cases):
        print(f"Maintained artifact: {DURABLE / 'index.html'}")
    else:
        print("Focused run complete; the maintained full-matrix artifact was preserved.")
    print(f"Complete: {len(selected)} captures. Regression baselines unchanged.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print(f"Screenshot catalog: {error}", file=sys.stderr)
        sys.exit(1)
