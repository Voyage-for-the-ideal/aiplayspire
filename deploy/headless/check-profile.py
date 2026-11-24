"""Read-only integrity check for the frozen, isolated game-profile snapshot."""
import hashlib
import json
from pathlib import Path
import sys


def check(root):
    root = Path(root).resolve(strict=True)
    baseline = root / "profile-baseline"
    manifest = json.loads((root / "profile-baseline-manifest.json").read_text(encoding="utf-8"))
    if baseline.is_symlink() or manifest.get("schema") != "headless-profile-baseline-v1":
        raise ValueError("Invalid profile baseline")
    actual = {}
    for path in sorted(baseline.rglob("*")):
        if path.is_symlink(): raise ValueError("Profile symlink is not a frozen input")
        if path.is_file(): actual[str(path.relative_to(baseline))] = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != manifest["files"]: raise ValueError("Profile baseline changed")
    return len(actual)


if __name__ == "__main__": check(sys.argv[1])
