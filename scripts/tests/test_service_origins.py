"""Keep Android's first-party authorities aligned with Curfew production."""

from pathlib import Path
import unittest

REPOSITORY = Path(__file__).resolve().parents[2]


class ServiceOriginTest(unittest.TestCase):
    def test_no_retired_first_party_service_authorities_in_shipping_source(self):
        retired = (
            "curfew-account" + ".hypertext.studio",
            "curfew-sync" + ".hypertext.studio",
        )
        violations = []
        for source in (REPOSITORY / "app/src/main").rglob("*.kt"):
            if any(host in source.read_text() for host in retired):
                violations.append(str(source.relative_to(REPOSITORY)))
        self.assertEqual(
            [], violations, "Retired first-party authorities remain in shipping code"
        )

    def test_production_services_are_declared_in_one_configuration(self):
        source = (
            REPOSITORY
            / "app/src/main/java/studio/hypertext/curfew/config/ProductionServices.kt"
        )
        self.assertTrue(
            source.is_file(),
            "Production service authorities need one shared configuration",
        )
        configuration = source.read_text()
        self.assertIn('"https://account.thecurfew.app"', configuration)
        self.assertIn('"https://sync.thecurfew.app"', configuration)
        for source in (REPOSITORY / "app/src/main").rglob("*.kt"):
            if source.name == "ProductionServices.kt":
                continue
            self.assertNotIn(
                '"https://account.thecurfew.app', source.read_text(), str(source)
            )
            self.assertNotIn(
                '"https://sync.thecurfew.app', source.read_text(), str(source)
            )


if __name__ == "__main__":
    unittest.main()
