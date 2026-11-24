import argparse
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path


def identity(worker: Path, manifest: dict | None = None) -> dict:
    with worker.open("rb") as stream:
        if stream.read(4) != b"\x7fELF":
            raise ValueError("expected a Linux ELF worker")
        stream.seek(0)
        sha = hashlib.file_digest(stream, "sha256").hexdigest()
    expected = {"schema": "battle-worker-release-v1", "build_type": "Release", "sha256": sha}
    if manifest is not None and expected != manifest:
        raise ValueError("worker and manifest differ")
    raw = subprocess.check_output([str(worker), "--build-info"], stdin=subprocess.DEVNULL, timeout=5)
    if json.loads(raw) != {"build_type": "Release"}:
        raise ValueError("worker does not report Release")
    return expected


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("stamp", "verify"))
    parser.add_argument("worker", type=Path)
    args = parser.parse_args()
    worker = args.worker.absolute()
    manifest = worker.with_name(worker.name + ".release.json")
    try:
        if worker.is_symlink() or manifest.is_symlink():
            raise ValueError("expected regular worker and manifest files")
        if args.mode == "verify":
            claimed = json.loads(manifest.read_text(encoding="utf-8"))
            if not isinstance(claimed, dict):
                raise ValueError("invalid release manifest")
            identity(worker, claimed)
        else:
            expected = identity(worker)
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=worker.parent, delete=False) as output:
                temporary = Path(output.name)
                json.dump(expected, output, sort_keys=True)
            try:
                temporary.replace(manifest)
            finally:
                temporary.unlink(missing_ok=True)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        parser.exit(78, f"BUILD_HEADLESS_WITH_RELEASE_PRESET: {error}\n")


if __name__ == "__main__":
    main()
