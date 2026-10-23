"""Expose only public source as read-only build assets, without duplicating source."""
from pathlib import Path
import re
import posixpath
from mkdocs.structure.files import File


def on_files(files, config, **kwargs):
    root = Path(config.config_file_path).parent
    for directory in ("apps", "contracts", "infra", "labs", "scenarios", "scripts"):
        for path in sorted((root / directory).rglob("*")):
            if not path.is_file() or any(p in {"target", ".venv", "__pycache__", ".pytest_cache", ".terraform", "build", ".import_linter_cache", ".ruff_cache"} or p.endswith(".egg-info") for p in path.parts):
                continue
            if path.suffix not in {".java", ".py", ".xml", ".yml", ".yaml", ".json", ".sql", ".tf", ".hcl", ".sh", ".toml"} and path.name != "Dockerfile":
                continue
            relative = path.relative_to(root).as_posix()
            files.append(File.generated(config, "source/" + relative, content=path.read_bytes()))
    return files


def on_page_markdown(markdown, page, config, **kwargs):
    """Keep repository-relative Markdown links valid in the generated site too."""
    root = Path(config.config_file_path).parent.resolve()
    source = Path(page.file.abs_src_path).parent

    def rewrite(match):
        target = match.group(1)
        if ":" in target or target.startswith("#"):
            return match.group(0)
        path, _, fragment = target.partition("#")
        resolved = (source / path).resolve()
        try:
            relative = resolved.relative_to(root).as_posix()
        except ValueError:
            return match.group(0)
        if relative.split("/")[0] not in {"apps", "contracts", "infra", "labs", "scenarios", "scripts"}:
            return match.group(0)
        link = posixpath.relpath("source/" + relative, posixpath.dirname(page.file.src_uri))
        return "](" + link + ("#" + fragment if fragment else "") + ")"
    return re.sub(r"\]\(([^)]+)\)", rewrite, markdown)
