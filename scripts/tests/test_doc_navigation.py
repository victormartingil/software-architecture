"""Negative cases must reject broken built navigation, not merely parse HTML."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("doc_navigation", Path(__file__).parents[1] / "check_doc_navigation.py")
navigation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(navigation)


class DocumentationNavigationTest(unittest.TestCase):
    def check(self, files):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, content in files.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding="utf-8")
            return navigation.check_site(root)

    def test_accepts_sequential_direct_encoded_and_asset_links(self):
        errors, pages, checked = self.check({
            "index.html": '<a id="start"></a><a href="chapter/#java">Next</a><a href="/chapter/?q=x#java">Direct</a><a href="#start">Self</a>',
            "chapter/index.html": '<h1 id="java">Java</h1><a id="repaso-código"></a><a href="../#start">Back</a><a href="#repaso-c%C3%B3digo">Encoded</a><a href="https://example.org/#external">External</a><img src="../figure.svg"><a href="../figure.svg#view">SVG</a>',
            "figure.svg": '<svg></svg>',
        })
        self.assertEqual(errors, [])
        self.assertEqual(pages, 2)
        self.assertEqual(checked, 7)

    def test_rejects_missing_destination(self):
        errors, _, _ = self.check({"index.html": '<a href="missing/#java">Broken</a>'})
        self.assertTrue(any("missing destination" in error for error in errors))

    def test_rejects_missing_fragment_in_existing_page(self):
        errors, _, _ = self.check({"index.html": '<a href="chapter/#missing">Broken</a>', "chapter/index.html": '<h1 id="java">Java</h1>'})
        self.assertTrue(any("missing fragment" in error for error in errors))

    def test_rejects_duplicate_ids(self):
        errors, _, _ = self.check({"index.html": '<a id="java"></a><h1 id="java">Java</h1>'})
        self.assertTrue(any("duplicate ID: java" in error for error in errors))

    def test_rejects_decoded_link_leaving_site(self):
        errors, _, _ = self.check({"index.html": '<a href="%2e%2e/private.html">Broken</a>'})
        self.assertTrue(any("link leaves site" in error for error in errors))

    def test_rejects_empty_build(self):
        errors, _, _ = self.check({})
        self.assertTrue(any("No built HTML" in error for error in errors))

    def test_does_not_confuse_same_id_on_different_pages_with_duplicate(self):
        errors, _, _ = self.check({"index.html": '<h1 id="java">One</h1>', "chapter/index.html": '<h1 id="java">Two</h1>'})
        self.assertEqual(errors, [])

    def test_cli_returns_failure_for_broken_navigation(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "index.html").write_text('<a href="#missing">Broken</a>')
            result = subprocess.run(
                [sys.executable, str(Path(__file__).parents[1] / "check_doc_navigation.py"), directory],
                capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 1)
            self.assertIn("missing fragment", result.stderr)
