"""Bundle an exact working-tree snapshot and explicit runtime assets; no secrets."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

from preflight import sha256

SOURCE_DIRS = ("battle_engine", "third_party/sts_lightspeed", "sts_ai_framework",
               "selectcard/src", "selectcard/policy", "cardcrawl", "deploy/linux",
               "STSStateSaver/src", "LudicrousSpeed/src", "scumthespire/src", "StSCommunicationMod/src")
EXCLUDE = {".git", "__pycache__", "build", "bin", "logs", "out", "target", ".venv"}
SUFFIXES = {".py", ".java", ".cpp", ".h", ".hpp", ".json", ".md", ".txt", ".xml",
            ".sh", ".conf", ".example", ".service", ".slice", ".target", ".properties", ".MIT"}


def sources(root):
    paths = set()
    for name in SOURCE_DIRS:
        for path in (root / name).rglob("*"):
            relative = path.relative_to(root)
            if path.is_file() and not any(part in EXCLUDE for part in relative.parts) and path.suffix in SUFFIXES:
                paths.add(relative)
    for name in ("AGENTS.md", "CLAUDE.md", "selectcard/CLAUDE.md", "selectcard/routing_calibration.py",
                 "battle_engine/CMakeLists.txt", "third_party/sts_lightspeed/CMakeLists.txt",
                 "STSStateSaver/pom.xml", "LudicrousSpeed/pom.xml", "scumthespire/pom.xml", "StSCommunicationMod/pom.xml",
                 "docs/LINUX_DEPLOYMENT.md"):
        paths.add(Path(name))
    return {str(path).replace("\\", "/"): root / path for path in paths}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    files = sources(root)
    for name in ("SaveStateMod.jar", "LudicrousSpeed.jar", "BattleAiMod.jar", "CommunicationMod.jar"):
        files["runtime-assets/mods/" + name] = root / "_ModTheSpire/mods" / name
    files["selectcard/checkpoints/best_sts_value_model_final.pth"] = root / "selectcard/checkpoints/best_sts_value_model_final.pth"
    for path in files.values():
        if not path.is_file():
            raise FileNotFoundError(path)
    manifest = {"schema": "linux-deployment-snapshot-v1", "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
                "working_tree_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=root)),
                "files": {name: sha256(path) for name, path in sorted(files.items())}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as output, tarfile.open(fileobj=output, mode="w:gz") as archive:
        for name, path in sorted(files.items()):
            archive.add(path, arcname=name, recursive=False)
        data = (json.dumps(manifest, indent=2) + "\n").encode()
        info = tarfile.TarInfo("deployment-manifest.json")
        info.size, info.mode = len(data), 0o644
        archive.addfile(info, io.BytesIO(data))
    digest = sha256(args.output)
    args.output.with_suffix(args.output.suffix + ".sha256").write_text(f"{digest}  {args.output.name}\n")
    print(json.dumps({"output": str(args.output.resolve()), "sha256": digest, "files": len(files)}, indent=2))


if __name__ == "__main__":
    main()
