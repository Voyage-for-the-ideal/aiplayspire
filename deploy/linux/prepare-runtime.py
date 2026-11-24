"""Create an isolated runtime and provenance manifest; starts no game/services."""
import argparse
import json
from pathlib import Path
import shutil

from preflight import MODS, inspect_mods, settings, sha256, verify_release


def write_initial(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        existing = path.read_text()
        same = existing == content
        if path.suffix == ".json":
            same = json.loads(existing) == json.loads(content)
        elif path.suffix == ".properties":
            def properties(text):
                return dict(line.split("=", 1) for line in text.splitlines()
                            if "=" in line and not line.lstrip().startswith(("#", "!")))
            actual, expected = properties(existing), properties(content)
            same = all(actual.get(key) == value for key, value in expected.items())
        if not same:
            raise ValueError(f"existing configuration differs; inspect before changing: {path}")
    else:
        with path.open("x", encoding="utf-8") as handle:
            handle.write(content)


def copy_initial(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if sha256(source) != sha256(target):
            raise ValueError(f"existing asset differs; inspect before replacing: {target}")
    else:
        shutil.copy2(source, target)


def ensure_jre(game, java=Path("/usr/bin/java")):
    """MTS also discovers Java for its Workshop helper, even with --jre51."""
    target = game / "jre/bin/java"
    if not java.is_file():
        raise ValueError(f"missing system Java: {java}")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() or target.is_symlink():
        if target.resolve() != java.resolve():
            raise ValueError(f"existing JRE differs; inspect before replacing: {target}")
    else:
        target.symlink_to(java)


def initialize_menu_profile(game, name):
    """Initialize only an empty profile selector; never import progress or a run."""
    target = game / "preferences/STSSaveSlots"
    data = json.loads(target.read_text()) if target.exists() else {}
    if not isinstance(data, dict):
        raise ValueError("profile selector must be a JSON object")
    if data:
        return False  # Preserve every existing profile and its selected slot.
    for folder in ("saves", "runs"):
        if any(path.is_file() for path in (game / folder).rglob("*")):
            raise ValueError("existing run files require manual profile setup")
    if not name.strip():
        raise ValueError("menu profile name must not be empty")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({"DEFAULT_SLOT": "0", "PROFILE_NAME": name}, indent=2) + "\n")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-game", type=Path, default=Path("/home/ubuntu/sts/lib"))
    parser.add_argument("--menu-profile", help="Explicitly initialize an empty profile selector; stop the game first")
    args = parser.parse_args()
    c = settings()
    if c["game"].resolve() == args.source_game.resolve():
        raise ValueError("deployment requires a separate game directory")
    for name in ("logs/agent", "logs/gateway", "traces", "reports", "home", "game/savestates", "game/startstates"):
        (c["runtime"] / name).mkdir(parents=True, exist_ok=True)
    for name in ("ModTheSpire.jar", "desktop-1.0.jar"):
        copy_initial(args.source_game / name, c["game"] / name)
    for name in MODS:
        source = (args.source_game / "mods" / name if name in ("BaseMod.jar", "StSLib.jar")
                  else c["root"] / "runtime-assets/mods" / name)
        copy_initial(source, c["game"] / "mods" / name)
    ensure_jre(c["game"])
    if args.menu_profile:
        import subprocess
        active = subprocess.run(["systemctl", "is-active", "--quiet", "aiplayspire-game.service"])
        if active.returncode == 0:
            raise ValueError("stop the game before initializing its menu profile")
        initialize_menu_profile(c["game"], args.menu_profile)
    config = c["home"] / ".config/ModTheSpire"
    write_initial(config / "mod_lists.json", json.dumps({"defaultList": c["profile"], "lists": {c["profile"]: list(MODS)}}, indent=2) + "\n")
    write_initial(config / "BattleAIMod/options.properties", "auto_start_ai=true\nsearch_profile=FAST\nbackend=external\n" + f"client_port={c['port']}\ncontroller_mode=INVISIBLE\n")
    write_initial(c["game"] / "info.displayconfig", "1024\n576\n30\nfalse\nfalse\nfalse\n")
    mods = inspect_mods(c["root"], c["game"])
    manifest = {"schema": "linux-collector-runtime-v1", "real_collection_started": False,
                "mods": mods, "worker_sha256": sha256(c["worker"]),
                "checkpoint_sha256": sha256(c["checkpoint"]),
                "release_manifest_sha256": verify_release(c["root"]),
                "backend": "cpp-only", "search_profile": "FAST", "timeout_ms": c["budget_ms"],
                "max_expansions": 5000, "java_heap_mib": 768, "route_policy": "planner"}
    write_initial(c["runtime"] / "runtime-manifest.json", json.dumps(manifest, indent=2) + "\n")
    print("Prepared isolated runtime; launch gates unchanged:", c["runtime"])


if __name__ == "__main__":
    main()
