"""Offline deployment checks; optional live checks only read state/PING."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

MODS = {
    "BaseMod.jar": "basemod", "StSLib.jar": "stslib",
    "SaveStateMod.jar": "SaveStateMod", "LudicrousSpeed.jar": "LudicrousSpeed",
    "BattleAiMod.jar": "BattleAiMod", "CommunicationMod.jar": "CommunicationMod",
}
PROJECT_MODS = {"SaveStateMod.jar": "STSStateSaver", "LudicrousSpeed.jar": "LudicrousSpeed",
                "BattleAiMod.jar": "scumthespire"}


def sha256(path):
    with Path(path).open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def settings():
    root = Path(os.environ.get("AIP_ROOT", Path(__file__).resolve().parents[2])).resolve()
    runtime = Path(os.environ.get("AIP_RUNTIME", "/home/ubuntu/aiplayspire-runtime")).resolve()
    return {"root": root, "runtime": runtime,
            "game": Path(os.environ.get("AIP_GAME_DIR", runtime / "game")),
            "home": Path(os.environ.get("AIP_HOME", runtime / "home")),
            "worker": Path(os.environ.get("AIP_WORKER", root / "battle_engine/bin/battle-engine-worker")),
            "checkpoint": Path(os.environ.get("STS_VALUE_CHECKPOINT", root / "selectcard/checkpoints/best_sts_value_model_final.pth")),
            "profile": os.environ.get("AIP_PROFILE", "Collector"),
            "port": int(os.environ.get("AIP_PORT", "5125")),
            "api_port": int(os.environ.get("AIP_API_PORT", "5000")),
            "budget_ms": int(os.environ.get("AIP_BUDGET_MS", "250"))}


def inspect_mods(root, game):
    inventory = {}
    for name, expected_id in MODS.items():
        path = game / "mods" / name
        with zipfile.ZipFile(path) as archive:
            info = json.loads(archive.read("ModTheSpire.json"))
        if info.get("modid") != expected_id:
            raise ValueError(f"unexpected mod ID: {name}")
        if name in PROJECT_MODS:
            pom = ET.parse(root / PROJECT_MODS[name] / "pom.xml").getroot()
            expected = pom.find("{http://maven.apache.org/POM/4.0.0}version").text
        elif name == "CommunicationMod.jar":
            expected = json.loads((root / "StSCommunicationMod/src/main/resources/ModTheSpire.json").read_text())["version"]
        else:
            expected = info.get("version")
        if info.get("version") != expected:
            raise ValueError(f"mod version mismatch: {name}: {info.get('version')} / {expected}")
        inventory[name] = {"modid": expected_id, "version": expected, "sha256": sha256(path)}
    return inventory


def check_profile(config):
    profile_path = config["home"] / ".config/ModTheSpire/mod_lists.json"
    lists = json.loads(profile_path.read_text())["lists"]
    if set(lists.get(config["profile"], [])) != set(MODS):
        raise ValueError("profile must enable exactly the six required mod JARs")
    options = (profile_path.parent / "BattleAIMod/options.properties").read_text()
    for line in ("auto_start_ai=true", "search_profile=FAST", "backend=external", f"client_port={config['port']}"):
        if line not in options.splitlines():
            raise ValueError("missing BattleAi option: " + line)
    if (config["game"] / "info.displayconfig").read_text().splitlines() != ["1024", "576", "30", "false", "false", "false"]:
        raise ValueError("unexpected display configuration")


def require_free_port(port):
    for host in ("127.0.0.1",):
        with socket.socket() as connection:
            connection.settimeout(.2)
            if connection.connect_ex((host, port)) == 0:
                raise ValueError(f"port already in use: {port}")


def check_jre(game, java=Path("/usr/bin/java")):
    target = game / "jre/bin/java"
    if not target.is_file() or target.resolve() != java.resolve() or not os.access(target, os.X_OK):
        raise ValueError("missing matching game/jre/bin/java; run prepare-runtime.py")


def verify_release(root):
    manifest = json.loads((root / "deployment-manifest.json").read_text())
    for name, expected in manifest["files"].items():
        path = (root / name).resolve()
        if not path.is_relative_to(root.resolve()) or sha256(path) != expected:
            raise ValueError("release fingerprint mismatch: " + name)
    return sha256(root / "deployment-manifest.json")


def check_model(path):
    import torch
    from selectcard.src.inference import STSInferenceEngine
    if torch.version.cuda is not None or torch.get_num_threads() != 1:
        raise ValueError("expected CPU-only torch and one inference thread")
    engine = STSInferenceEngine(model_path=path)
    if any(parameter.device.type != "cpu" for parameter in engine.model.parameters()):
        raise ValueError("model is not on CPU")
    return {"sha256": sha256(path), "format": 12, "torch": torch.__version__,
            "architecture": engine.model.model_config}


def check(mode, model=False):
    c = settings()
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise ValueError("expected Linux x86_64")
    for tool in ("java", "xvfb-run", "Xvfb", "xauth", "xrandr", "flock"):
        if shutil.which(tool) is None:
            raise ValueError("missing tool: " + tool)
    java = subprocess.run(["java", "-version"], capture_output=True, text=True, check=True)
    if 'version "1.8.' not in java.stderr:
        raise ValueError("game runtime requires Java 8")
    if not 1 <= c["budget_ms"] <= 250 or c["port"] == c["api_port"]:
        raise ValueError("low-memory profile requires distinct ports and a 1..250 ms ceiling")
    if not c["worker"].is_file() or not os.access(c["worker"], os.X_OK):
        raise ValueError("missing executable native worker")
    if c["worker"].read_bytes()[:4] != b"\x7fELF":
        raise ValueError("native worker must be a Linux ELF binary")
    for name in ("ModTheSpire.jar", "desktop-1.0.jar"):
        if not (c["game"] / name).is_file():
            raise ValueError("missing game asset: " + name)
    for name in ("requests", "pydantic", "dotenv", "colorama", "openai", "torch", "numpy", "pandas", "pyarrow", "sklearn"):
        if importlib.util.find_spec(name) is None:
            raise ValueError("missing CPU dependency: " + name)
    check_profile(c)
    check_jre(c["game"])
    inventory = inspect_mods(c["root"], c["game"])
    release = verify_release(c["root"])
    runtime = json.loads((c["runtime"] / "runtime-manifest.json").read_text())
    if runtime["mods"] != inventory or runtime["worker_sha256"] != sha256(c["worker"]) or runtime["checkpoint_sha256"] != sha256(c["checkpoint"]) or runtime["release_manifest_sha256"] != release:
        raise ValueError("runtime manifest drift")
    memory = dict(line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines())
    available_mib = int(memory["MemAvailable"].split()[0]) // 1024
    if mode == "offline":
        for gate in ("ALLOW_GAME", "ALLOW_COLLECTION"):
            if (c["runtime"] / gate).exists():
                raise ValueError("offline validation requires closed launch gates: " + gate)
        for port in {c["port"], c["api_port"], 5124, 5126}:
            require_free_port(port)
        if available_mib < 2304:
            raise ValueError(f"offline validation needs 2304 MiB available, got {available_mib}")
    elif mode == "game":
        require_free_port(c["api_port"])
    else:
        if not os.environ.get("DEEPSEEK_API_KEY"):
            raise ValueError("collector credentials not configured")
        if not (c["root"] / "cardcrawl/helpers/MonsterHelper.java").is_file():
            raise ValueError("missing read-only content catalog")
    if shutil.disk_usage(c["runtime"]).free < 3 * 1024**3:
        raise ValueError("less than 3 GiB disk space remaining")
    result = {"ok": True, "mode": mode, "available_memory_mib": available_mib,
              "mods": inventory, "release_manifest_sha256": release,
              "launch_gates": {gate: (c["runtime"] / gate).is_file() for gate in ("ALLOW_GAME", "ALLOW_COLLECTION")}}
    if model:
        result["model"] = check_model(c["checkpoint"])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("offline", "game", "collector"), default="offline")
    parser.add_argument("--model", action="store_true")
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.mode, args.model), ensure_ascii=False, indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(f"PREFLIGHT_FAILED: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
