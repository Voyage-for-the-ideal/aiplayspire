"""Run the real vanilla shop oracle in an isolated copied runtime/profile.

No production deployment; uses only BaseMod and the explicitly built bridge.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--bridge-jar", type=Path, default=Path("StSCommunicationMod/target/CommunicationMod.jar"))
    parser.add_argument("--count", type=int, default=200)
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=False)
    runtime = output / "runtime"
    mods = runtime / "mods"
    mods.mkdir(parents=True)
    game = args.game_dir.resolve()
    for name in ("desktop-1.0.jar", "ModTheSpire.jar"):
        shutil.copy2(game / name, runtime / name)
    # Use installed Java 8 runtime; no download or dependency installation.
    shutil.copytree(game / "jre", runtime / "jre")
    shutil.copy2(game / "mods" / "BaseMod.jar", mods / "BaseMod.jar")
    shutil.copy2(args.bridge_jar.resolve(), mods / "CommunicationMod.jar")
    environment = os.environ.copy()
    environment.update(LOCALAPPDATA=str(runtime / "appdata"), APPDATA=str(runtime / "appdata"))
    config = runtime / "appdata" / "ModTheSpire"
    config.mkdir(parents=True)
    (config / "mod_lists.json").write_text(json.dumps({"defaultList": "ShopProbe", "lists": {"ShopProbe": ["BaseMod.jar", "CommunicationMod.jar"]}}), encoding="utf-8")
    hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (runtime / "desktop-1.0.jar", mods / "BaseMod.jar", mods / "CommunicationMod.jar")}
    (output / "manifest.json").write_text(json.dumps({"hashes": hashes, "count": args.count,
                "scope": "initial vanilla stock and controlled purchase transitions; not historical recovery"}, indent=2), encoding="utf-8")
    fixtures = output / "shops.jsonl"
    command = [str(runtime / "jre" / "bin" / "java.exe"), "-Dspire.isolatedResearch=true",
               f"-Dspire.shopProbeOutput={fixtures}", f"-Dspire.shopProbeCount={args.count}",
               "-Xmx2048m", "-jar", "ModTheSpire.jar", "--profile", "ShopProbe", "--skip-launcher", "--skip-intro", "--jre51"]
    with (output / "engine.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=runtime, env=environment, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=15)
            raise RuntimeError(f"Oracle timed out; inspect {output / 'engine.log'}")
    text = (output / "engine.log").read_text(encoding="utf-8", errors="replace")
    if code or "SHOP_PROBE_COMPLETE" not in text:
        raise RuntimeError(f"Oracle did not complete (exit={code}); inspect {output / 'engine.log'}")
    print(fixtures)


if __name__ == "__main__":
    main()
