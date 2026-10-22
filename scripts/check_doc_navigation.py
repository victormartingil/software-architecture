"""Check local destinations, fragments and duplicate IDs in built MkDocs HTML.

Uses only the standard library and never requests external URLs. This complements
the strict build; it does not render GitHub Markdown or prove visual accessibility.
"""
from __future__ import annotations

from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids: set[str] = set()
        self.duplicates: set[str] = set()
        self.links: list[str] = []

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name == "id" and value:
                if value in self.ids:
                    self.duplicates.add(value)
                self.ids.add(value)
            if name in {"href", "src"} and value:
                self.links.append(value)

    handle_startendtag = handle_starttag


def check_site(directory: Path) -> tuple[list[str], int, int]:
    root = directory.resolve()
    errors: list[str] = []
    pages: dict[Path, Page] = {}
    checked = 0
    for path in sorted(root.rglob("*.html")):
        if not path.resolve().is_relative_to(root):
            errors.append(f"HTML leaves site directory: {path.relative_to(root)}")
            continue
        page = Page()
        page.feed(path.read_text(encoding="utf-8"))
        page.close()
        pages[path.resolve()] = page
        for identity in sorted(page.duplicates):
            errors.append(f"{path.relative_to(root)}: duplicate ID: {identity}")
    if not pages:
        errors.append("No built HTML pages found; run the strict MkDocs build first")
    for source, page in pages.items():
        for link in page.links:
            parts = urlsplit(link)
            if parts.scheme or parts.netloc:
                continue
            checked += 1
            location = unquote(parts.path)
            if not location:
                target = source
            elif location.startswith("/"):
                target = (root / location.lstrip("/")).resolve()
            else:
                target = (source.parent / location).resolve()
            if not target.is_relative_to(root):
                errors.append(f"{source.relative_to(root)}: link leaves site: {link}")
                continue
            if target.is_dir():
                target = (target / "index.html").resolve()
                if not target.is_relative_to(root):
                    errors.append(f"{source.relative_to(root)}: link leaves site: {link}")
                    continue
            if not target.is_file():
                errors.append(f"{source.relative_to(root)}: missing destination: {link}")
                continue
            fragment = unquote(parts.fragment)
            if fragment and target.suffix.lower() == ".html":
                destination = pages.get(target)
                if destination is None or fragment not in destination.ids:
                    errors.append(f"{source.relative_to(root)}: missing fragment: {link}")
    return errors, len(pages), checked


def main() -> int:
    directory = Path(sys.argv[1]) if len(sys.argv) == 2 else Path(__file__).resolve().parents[1] / "site"
    if len(sys.argv) > 2:
        print("Usage: python3 scripts/check_doc_navigation.py [built-site-directory]", file=sys.stderr)
        return 2
    errors, pages, links = check_site(directory)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Built documentation navigation: {pages} HTML pages, {links} local links checked; IDs and fragments passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
