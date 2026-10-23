import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest


@pytest.mark.parametrize(
    ('source_layer', 'broken_contracts', 'kept_contracts'),
    [
        # Application imports domain: contaminating domain also creates a forbidden indirect import.
        ('domain', ('Domain is independent', 'Application uses ports'), ()),
        ('application', ('Application uses ports',), ('Domain is independent',)),
    ],
)
def test_architecture_rule_rejects_deliberate_infrastructure_import(
    tmp_path, source_layer, broken_contracts, kept_contracts
):
    service = Path(__file__).parents[1]
    shutil.copytree(service/'src',tmp_path/'src')
    shutil.copy(service/'pyproject.toml',tmp_path/'pyproject.toml')
    source = tmp_path/f'src/ai_service/{source_layer}/__init__.py'
    source.write_text(source.read_text()+'\nimport ai_service.infrastructure.retrieval\n')
    env = {**os.environ,'PYTHONPATH':str(tmp_path/'src')}
    result = subprocess.run([str(Path(sys.executable).parent/'lint-imports'),'--no-cache'],cwd=tmp_path,
                            env=env,capture_output=True,text=True)
    assert result.returncode == 1, result.stdout + result.stderr
    for contract in broken_contracts:
        assert f'{contract} BROKEN' in result.stdout
    for contract in kept_contracts:
        assert f'{contract} KEPT' in result.stdout
    assert f'Contracts: {len(kept_contracts)} kept, {len(broken_contracts)} broken.' in result.stdout
    assert f'ai_service.{source_layer} -> ai_service.infrastructure.retrieval' in result.stdout
