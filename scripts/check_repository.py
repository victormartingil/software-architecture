#!/usr/bin/env python3
"""Check public source hygiene and local links without importing project dependencies.

This is a deterministic publication hygiene check, not a secret scanner or a full
Markdown/JSON Schema parser. CI runs dependency security scanners separately.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {
    ".local", ".git", ".venv", "target", "node_modules", ".terraform", "site",
    "reports", "__pycache__", ".pytest_cache", ".ruff_cache", ".import_linter_cache",
    "build", ".cache",
}
TEXT_SUFFIXES = {
    ".md", ".py", ".java", ".json", ".yaml", ".yml", ".toml", ".xml", ".sql",
    ".tf", ".hcl", ".sh", ".properties", ".txt", ".ini", ".cfg", ".html", ".svg", ".drawio",
}
HOME_PATH = re.compile(r"(?:/Users/|/home/|[A-Za-z]:[\\/]Users[\\/])[A-Za-z0-9][A-Za-z0-9._-]*")
PRIVATE_KEY = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
EMAIL = re.compile(r"\b[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})\b")
INLINE_LINK = re.compile(r"!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+['\"][^\n]*?['\"])?\s*\)")
REFERENCE_LINK = re.compile(r"^\s*\[[^\]]+\]:\s*(<[^>]+>|\S+)", re.MULTILINE)
HTML_LINK = re.compile(r"(?:src|href)\s*=\s*['\"]([^'\"]+)['\"]", re.IGNORECASE)


def public_files():
    for directory, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in EXCLUDED and not d.endswith(".egg-info")]
        for name in dirs:
            if (Path(directory) / name).is_symlink():
                yield Path(directory) / name
        for name in files:
            yield Path(directory) / name


def line_number(text: str, position: int) -> int:
    return text.count("\n", 0, position) + 1


def check_link(source: Path, destination: str, errors: list[str]):
    destination = destination.strip("<>")
    parts = urlsplit(destination)
    if parts.scheme or parts.netloc:
        if parts.scheme == "file":
            errors.append(f"{source.relative_to(ROOT)}: machine-specific file URI")
        return
    if not parts.path:
        return  # Anchor validity belongs to the documentation renderer.
    value = re.sub(r":\d+$", "", unquote(parts.path))
    target = (source.parent / value).resolve()
    if not target.is_relative_to(ROOT) or ".local" in target.relative_to(ROOT).parts:
        errors.append(f"{source.relative_to(ROOT)}: link leaves public repository: {destination}")
    elif not target.exists():
        errors.append(f"{source.relative_to(ROOT)}: missing link target: {destination}")


def check_tracked_files(errors: list[str]):
    if not (ROOT / ".git").exists():
        return
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True)
    for name in result.stdout.decode().split("\0"):
        if not name:
            continue
        path = Path(name)
        if any(part in EXCLUDED or part.endswith(".egg-info") for part in path.parts):
            errors.append(f"tracked private/generated file: {name}")
        if path.suffix in {".pyc", ".class", ".jar", ".tfstate"} or path.name.startswith(".env") and path.name != ".env.example":
            errors.append(f"tracked generated/credential file: {name}")


def main() -> int:
    errors: list[str] = []
    files = list(public_files())
    markdown_count = 0
    for path in files:
        if path.is_symlink():
            resolved = path.resolve()
            if not resolved.is_relative_to(ROOT) or ".local" in resolved.relative_to(ROOT).parts:
                errors.append(f"public symlink exposes a private/external path: {path.relative_to(ROOT)}")
            continue
        if path.name.startswith(".env") and path.name != ".env.example":
            errors.append(f"public credential configuration: {path.relative_to(ROOT)}")
        if path.suffix not in TEXT_SUFFIXES and path.name not in {"Makefile", "Dockerfile", ".gitignore", ".dockerignore", "mvnw"}:
            continue
        text = path.read_text(encoding="utf-8")
        for pattern, reason in ((HOME_PATH, "machine-specific home path"), (PRIVATE_KEY, "private key material")):
            for match in pattern.finditer(text):
                errors.append(f"{path.relative_to(ROOT)}:{line_number(text, match.start())}: {reason}")
        for match in EMAIL.finditer(text):
            # Kafka's numeric brokerId@host:port is an address, not an email.
            if match.group(0).split("@", 1)[0].isdigit() and re.match(r":\d+\b", text[match.end():]):
                continue
            domain = match.group(1).lower()
            if not (domain in {"example.com", "example.org", "example.net"} or domain.endswith((".example", ".invalid", ".test"))):
                errors.append(f"{path.relative_to(ROOT)}:{line_number(text, match.start())}: non-synthetic email")
        if path.suffix == ".md":
            markdown_count += 1
            prose = re.sub(r"(?ms)^\s*(```|~~~).*?^\s*\1\s*$", "", text)
            prose = re.sub(r"`[^`\n]+`", "", prose)
            for pattern in (INLINE_LINK, REFERENCE_LINK, HTML_LINK):
                for match in pattern.finditer(prose):
                    check_link(path, match.group(1), errors)
    ignore = (ROOT / ".gitignore").read_text()
    for required in ("/.local/", "**/target/", "**/.venv/", "**/.terraform/", "/site/", "/reports/"):
        if required not in ignore.splitlines():
            errors.append(f"missing generated/private ignore rule: {required}")
    fixtures = list((ROOT / "contracts/events/fixtures").glob("*.json"))
    if not fixtures:
        errors.append("no event fixtures")
    for path in fixtures + list((ROOT / "contracts/events").glob("*.schema.json")):
        try:
            if not isinstance(json.loads(path.read_text()), dict):
                errors.append(f"JSON contract is not an object: {path.relative_to(ROOT)}")
        except (ValueError, OSError):
            errors.append(f"invalid JSON contract: {path.relative_to(ROOT)}")
    check_tracked_files(errors)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Repository hygiene: {markdown_count} Markdown files; links and public source checks passed")
    print(f"JSON sanity: {len(fixtures)} event fixtures parsed; full Schema checks run in application tests")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
