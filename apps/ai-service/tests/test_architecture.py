import os
import shutil
import subprocess
import sys
from pathlib import Path


def test_architecture_rule_rejects_deliberate_infrastructure_import(tmp_path):
    service = Path(__file__).parents[1]
    shutil.copytree(service/'src',tmp_path/'src')
    shutil.copy(service/'pyproject.toml',tmp_path/'pyproject.toml')
    domain = tmp_path/'src/ai_service/domain/__init__.py'
    domain.write_text(domain.read_text()+'\nimport ai_service.infrastructure.retrieval\n')
    env = {**os.environ,'PYTHONPATH':str(tmp_path/'src')}
    result = subprocess.run([str(Path(sys.executable).parent/'lint-imports'),'--no-cache'],cwd=tmp_path,
                            env=env,capture_output=True,text=True)
    assert result.returncode != 0
    assert 'Domain is independent BROKEN' in result.stdout
