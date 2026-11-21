"""Filesystem paths for current packages and immutable flat-layout runs."""
from pathlib import Path


def package_layout(repo):
    return (repo/'sts_ai_framework/headless/__init__.py').is_file()


def module_path(repo, name):
    framework=repo/'sts_ai_framework'
    if package_layout(repo):
        return framework/'headless'/f'{name}.py'
    return framework/f'headless_{name}.py'


def runtime_files(root, native=None):
    """Hash the actual layout; do not translate paths in archived identities."""
    repo=root/'repo'
    framework=repo/'sts_ai_framework'
    if package_layout(repo):
        modules=(framework/'headless').rglob('*.py')
    else:
        modules=framework.glob('headless_*.py')
    return [
        Path(native) if native is not None else root/'native-bin/battle-engine-worker',
        *sorted((root/'tools').glob('*.java')),
        *sorted((root/'classes').glob('*.class')),
        *sorted((root/'tools').glob('*.sh')),
        *sorted(modules),
    ]
