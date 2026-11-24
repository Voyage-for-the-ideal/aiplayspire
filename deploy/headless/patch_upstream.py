"""Apply the small pre-run fix to the pinned archive, without accepting drift."""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
from pathlib import Path
import tarfile

COMMIT = "b354a9ee8e118e906d9b9f55ba043100cc1ac6c7"
ARCHIVE_SHA256 = "954fe6309a7cad5cd9ea27efbebeb2093fa37f21affd4f040cb13a117d1fe02b"
PREFIX = "headless-src/com/megacrit/cardcrawl/headless/"
EDITS = {
    PREFIX + "GameKernel.java": (
        ("    public Phase phase() {\n", "    public Phase phase() {\n"
         "        // No player/room exists before newRun; a read must not advance UI.\n"
         "        if (AbstractDungeon.player == null) {\n"
         "            return AbstractDungeon.currMapNode == null ? Phase.BOOT : null;\n"
         "        }\n"),
        # The room-null handling that follows this line already represents transitions.
        ("        AbstractRoom room = AbstractDungeon.getCurrRoom();\n        if (room == null) return null;\n",
         "        AbstractRoom room = AbstractDungeon.currMapNode == null\n"
         "                ? null : AbstractDungeon.getCurrRoom();\n        if (room == null) return null;\n"),
    ),
    PREFIX + "HeadlessObservation.java": (
        ('        o.put("phase", phase.name());', '        o.put("phase", phase == null ? null : phase.name());'),
        ("        AbstractRoom room = AbstractDungeon.getCurrRoom();",
         "        AbstractRoom room = AbstractDungeon.currMapNode == null\n"
         "                ? null : AbstractDungeon.getCurrRoom();"),
    ),
}


def patched(relative: str, original: bytes) -> bytes:
    text = original.decode("utf-8")
    for before, after in EDITS[relative]:
        if text.count(before) != 1:
            raise ValueError("Patch context must match once: " + relative)
        text = text.replace(before, after, 1)
    return text.encode("utf-8")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def apply(root: Path) -> dict:
    archive = root / "install/upstream-source.tar.gz"
    if sha(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError("Pinned archive fingerprint differs")
    plans = []
    changes = []
    diffs = []
    checked = 0
    # Validate the entire source tree before touching either file.
    with tarfile.open(archive) as stream:
        for member in stream.getmembers():
            if not member.isfile():
                continue
            relative = Path(*Path(member.name).parts[1:])
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("Archive path traversal")
            original = stream.extractfile(member).read()
            name = relative.as_posix()
            expected = patched(name, original) if name in EDITS else original
            target = root / "upstream" / relative
            if target.is_symlink():
                raise ValueError("Source symlink: " + name)
            current = target.read_bytes()
            if current not in (original, expected):
                raise ValueError("Unexpected source change: " + name)
            if name in EDITS:
                changes.append({"path": name, "original_sha256": sha(original), "patched_sha256": sha(expected)})
                diffs.extend(difflib.unified_diff(original.decode().splitlines(True), expected.decode().splitlines(True),
                                                 fromfile="a/" + name, tofile="b/" + name))
                if current != expected:
                    plans.append((target, expected))
            checked += 1
    if len(changes) != len(EDITS):
        raise ValueError("Archive is missing patch targets")
    for target, data in plans:
        temporary = target.with_name(target.name + ".poc-tmp")
        temporary.write_bytes(data)
        temporary.chmod(target.stat().st_mode & 0o777)
        temporary.replace(target)
    report = {"upstream_commit": COMMIT, "patch_id": "pre-run-state-v1", "files_checked": checked,
              "files_written": len(plans), "changes": changes}
    (root / "reports/pre-run-state.patch").write_text("".join(diffs), encoding="utf-8")
    (root / "reports/patched-source-integrity.json").write_text(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    print(json.dumps(apply(parser.parse_args().root.resolve(strict=True)), indent=2))
