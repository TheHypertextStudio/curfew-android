import base64
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("catalog", Path(__file__).parents[1] / "screenshot_catalog.py")
catalog = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(catalog)


class CatalogTests(unittest.TestCase):
    def test_consumer_code_states_are_in_the_maintained_matrix(self):
        states = {state["id"] for state in catalog.load_matrix()["states"]}
        self.assertTrue({"place_intro", "place_code", "place_ready", "place_error", "place_busy", "place_camera_required"} <= states)

    def test_matrix_ids_are_unique_and_include_all_dimensions(self):
        cases = catalog.expand_matrix(catalog.load_matrix())
        self.assertEqual(len(cases), len({case["id"] for case in cases}))
        self.assertEqual({c["appearance"] for c in cases}, {"light", "dark"})
        self.assertGreaterEqual(len({c["device"] for c in cases}), 7)
        self.assertTrue(any(c["state"] == "recovery_save" and c["position"] == "top" for c in cases))

    def test_resume_reuses_only_matching_inputs_and_ui_fingerprint(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            renders = root / "renders"
            renders.mkdir()
            (renders / f'{case["function"]}_abc_0.png').write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            output = root / "output"
            catalog.build_catalog([case], renders, output)
            self.assertEqual(len(catalog.reusable_captures([case], output)), 1)
            with self.assertRaises(ValueError):
                catalog.reusable_captures([case], output, distribution="gms")
            self.assertEqual(catalog.reusable_captures([dict(case, width=999)], output), [])
            manifest = json.loads((output / "manifest.json").read_text())
            manifest["coverageContractSha256"] = "changed"
            (output / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaises(ValueError):
                catalog.reusable_captures([case], output)

    def test_missing_image_is_explicit_and_not_success(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as root:
            target = Path(root)
            complete = catalog.build_catalog([case], target / "renders", target / "output")
            self.assertFalse(complete)
            manifest = json.loads((target / "output/manifest.json").read_text())
            self.assertEqual(manifest["missing"], 1)
            self.assertIsNone(manifest["captures"][0]["image"])
            self.assertIn("Missing capture", (target / "output/index.html").read_text())

    def test_success_copies_image_and_escapes_labels(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        case["title"] = '<script>alert("bad")</script>'
        with tempfile.TemporaryDirectory() as root:
            target = Path(root)
            renders = target / "renders"
            renders.mkdir()
            # The official renderer names captures <function>_<parameter hashes>_0.png.
            (renders / f'{case["function"]}_abc_def_0.png').write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            self.assertTrue(catalog.build_catalog([case], renders, target / "output"))
            html = (target / "output/index.html").read_text()
            self.assertNotIn('<script>alert("bad")</script>', html)
            self.assertIn("&lt;script&gt;", html)
            self.assertTrue((target / f'output/images/{case["id"]}.png').is_file())

    def test_ambiguous_and_invalid_captures_fail(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as root:
            target = Path(root)
            for suffix in ("_abc", "_def"):
                image = target / "renders" / f'{case["function"]}{suffix}.png'
                image.parent.mkdir(exist_ok=True)
                image.write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            self.assertFalse(catalog.build_catalog([case], target / "renders", target / "output"))

    def test_renderer_failure_cannot_claim_complete_with_images(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as root:
            target = Path(root)
            renders = target / "renders"
            renders.mkdir()
            (renders / f'studio.hypertext.curfew.screenshots.CatalogPreviewsKt.{case["function"]}_abc_0.png').write_bytes(
                base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            self.assertFalse(catalog.build_catalog([case], renders, target / "output", renderer_ok=False))
            manifest = json.loads((target / "output/manifest.json").read_text())
            self.assertEqual(manifest["captured"], 1)
            self.assertFalse(manifest["complete"])

    def test_corrupt_image_is_not_captured(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as root:
            target = Path(root)
            renders = target / "renders"
            renders.mkdir()
            (renders / f'{case["function"]}_abc_0.png').write_bytes(b"not a png")
            self.assertFalse(catalog.build_catalog([case], renders, target / "output"))
            manifest = json.loads((target / "output/manifest.json").read_text())
            self.assertEqual(manifest["captures"][0]["status"], "invalid")

    def test_fixture_enum_matches_declared_states(self):
        source = (catalog.PREVIEWS.parent / "CatalogFixtures.kt").read_text()
        enum = source.split("enum class CatalogState {", 1)[1].split("}", 1)[0]
        states = {value.strip().lower() for value in enum.split(",") if value.strip()}
        self.assertEqual(states, {state["id"] for state in catalog.load_matrix()["states"]})

    def test_full_catalog_has_durable_publication_and_failed_runs_preserve_it(self):
        cases = catalog.expand_matrix(catalog.load_matrix())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, target = root / "run", root / "artifact"
            source.mkdir()
            (source / "index.html").write_text("first success")
            manifest = {"complete": True, "captures": [{"id": c["id"]} for c in cases]}
            (source / "manifest.json").write_text(json.dumps(manifest))
            self.assertTrue(catalog.publish_catalog(source, cases, target))
            self.assertEqual((target / "index.html").read_text(), "first success")
            manifest["complete"] = False
            (source / "manifest.json").write_text(json.dumps(manifest))
            (source / "index.html").write_text("failed attempt")
            self.assertFalse(catalog.publish_catalog(source, cases, target))
            self.assertEqual((target / "index.html").read_text(), "first success")

    def test_focused_run_does_not_replace_complete_artifact(self):
        cases = catalog.expand_matrix(catalog.load_matrix())
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "run"
            source.mkdir()
            (source / "manifest.json").write_text(json.dumps({"complete": True, "captures": [{"id": cases[0]["id"]}]}))
            self.assertFalse(catalog.publish_catalog(source, cases, Path(directory) / "artifact"))

    def test_coverage_contract_matches_current_sources_and_profile_matrix(self):
        self.assertEqual(catalog.audit_coverage(catalog.load_matrix()), [])
        for state in catalog.load_matrix()["states"]:
            self.assertIn("top", state["positions"])
        profiles = catalog.load_matrix()["devices"]
        self.assertEqual(len(profiles), 9)
        self.assertEqual(next(p for p in profiles if p["id"] == "large_text")["fontScale"], 2)

    def test_source_drift_and_new_ui_files_require_coverage_review(self):
        contract = json.loads((catalog.ROOT / "config/screenshots/coverage.json").read_text())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ["config/screenshots/coverage.json", *contract["reviewedSources"]]:
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((catalog.ROOT / name).read_bytes())
            self.assertEqual(catalog.audit_coverage(catalog.load_matrix(), root), [])
            source = root / next(iter(contract["reviewedSources"]))
            source.write_text(source.read_text() + "\n// new UI behavior\n")
            added = root / "app/src/main/java/studio/hypertext/curfew/ui/NewScreen.kt"
            added.write_text("@Composable fun NewScreen() {}")
            issues = catalog.audit_coverage(catalog.load_matrix(), root)
            self.assertTrue(any("Changed UI source" in issue for issue in issues))
            self.assertTrue(any("New UI source" in issue for issue in issues))

    def test_early_coverage_failure_replaces_stale_success_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "index.html").write_text("Complete old run")
            catalog.write_failure_report("New UI source <needs review>", root)
            manifest = json.loads((root / "manifest.json").read_text())
            self.assertFalse(manifest["complete"])
            self.assertIn("needs review", manifest["failure"])
            html = (root / "index.html").read_text()
            self.assertNotIn("Complete old run", html)
            self.assertIn("&lt;needs review&gt;", html)

    def test_viewer_refresh_preserves_pixels_and_capture_manifest(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            renders = root / "renders"
            renders.mkdir()
            (renders / f'{case["function"]}_abc_0.png').write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            output = root / "report"
            catalog.build_catalog([case], renders, output)
            before = (output / "manifest.json").read_bytes()
            image = output / f'images/{case["id"]}.png'
            pixels_before = image.read_bytes()
            with patch.object(catalog, "expand_matrix", return_value=[case]):
                catalog.refresh_catalog_viewer(output)
            self.assertEqual(image.read_bytes(), pixels_before)
            self.assertEqual((output / "manifest.json").read_bytes(), before)
            self.assertTrue((output / "README.md").is_file())
            self.assertTrue((output / "coverage.json").is_file())
            self.assertIn("Present screenshots", (output / "index.html").read_text())

    def test_render_batches_cover_the_full_matrix_once_and_bound_memory(self):
        cases = catalog.expand_matrix(catalog.load_matrix())
        batches = catalog.render_batches(cases, 150)
        self.assertGreater(len(batches), 1)
        self.assertTrue(all(len(batch) <= 150 for batch in batches))
        self.assertEqual([case["id"] for batch in batches for case in batch], [case["id"] for case in cases])
        with self.assertRaises(ValueError):
            catalog.render_batches(cases, 0)

    def test_viewer_refresh_uses_durable_success_after_build_report_is_removed(self):
        case = catalog.expand_matrix(catalog.load_matrix())[0]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            renders = root / "renders"
            renders.mkdir()
            (renders / f'{case["function"]}_abc_0.png').write_bytes(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZlWQAAAAASUVORK5CYII="))
            report, durable = root / "build-report", root / "artifact"
            catalog.build_catalog([case], renders, report)
            catalog.publish_catalog(report, [case], durable)
            import shutil
            shutil.rmtree(report)
            before = (durable / "manifest.json").read_bytes()
            with patch.object(catalog, "expand_matrix", return_value=[case]):
                catalog.refresh_maintained_viewer([case], report, durable)
            self.assertEqual((durable / "manifest.json").read_bytes(), before)
            self.assertIn("Present screenshots", (durable / "index.html").read_text())

    def test_generation_is_deterministic_and_matches_matrix(self):
        cases = catalog.expand_matrix(catalog.load_matrix())
        source = catalog.preview_source(cases)
        self.assertEqual(source.count("@PreviewTest"), len(cases))
        self.assertEqual(source, catalog.preview_source(cases))
        self.assertIn("CatalogState.RECOVERY_SAVE", source)


if __name__ == "__main__":
    unittest.main()
