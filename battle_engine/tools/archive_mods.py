"""Archive local mod sources in fork branches using gh's Git Data API.

No Git credential/config changes, transport clone, generated files or main-branch writes.
Run before changing mod sources. Each remote blob is checked against its local bytes.
"""
import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "battle_engine/out/pre-lightspeed-migration"


def run(args):
    p = subprocess.run(args, cwd=ROOT, text=True, encoding="utf-8", capture_output=True)
    if p.returncode:
        raise RuntimeError(f"{args}: {p.stderr}")
    return p.stdout.strip()


def api(path, data=None):
    args = ["gh", "api", path]
    if data is not None:
        payload = OUT / "api-payload.json"
        payload.write_text(json.dumps(data, ensure_ascii=True), encoding="utf-8")
        args += ["--method", "POST", "--input", str(payload)]
    return json.loads(run(args))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    user = api("user")
    assert user["login"] == "Voyage-for-the-ideal", "Check the active gh account"
    base = run(["git", "rev-parse", "HEAD"])
    records = []
    for mod in ("scumthespire", "STSStateSaver", "LudicrousSpeed"):
        repo = f"{user['login']}/{mod}"
        try:
            meta = api("repos/" + repo)
        except RuntimeError:
            run(["gh", "repo", "fork", f"boardengineer/{mod}", "--clone=false"])
            meta = api("repos/" + repo)
        assert meta["fork"] and meta["parent"]["full_name"].lower() == f"boardengineer/{mod}".lower()
        branch = "codex/pre-lightspeed-migration"
        # This command is intentionally a one-time freeze. Never move an
        # existing archive branch to a subsequently modified working tree.
        try:
            existing = api(f"repos/{repo}/git/ref/heads/{branch}")
        except RuntimeError as error:
            if "404" not in str(error):
                raise
        else:
            raise RuntimeError(f"Archive already exists: {repo}@{existing['object']['sha']}")
        head = api(f"repos/{repo}/git/ref/heads/{meta['default_branch']}")["object"]["sha"]
        commit = api(f"repos/{repo}/git/commits/{head}")
        files = run(["git", "ls-files", "--cached", "--others", "--exclude-standard", "--", mod]).splitlines()
        entries, manifest = [], []
        for filename in files:
            source = ROOT / filename
            rel = source.relative_to(ROOT / mod).as_posix()
            data = source.read_bytes()
            assert source.suffix not in (".jar", ".class", ".log", ".key", ".pem")
            digest = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
            manifest.append(dict(path=rel, sha256=hashlib.sha256(data).hexdigest(), git_blob=digest))
            entry = dict(path=rel, mode="100644", type="blob")
            try:
                entry["content"] = data.decode("utf-8")
            except UnicodeDecodeError:
                entry["sha"] = api(f"repos/{repo}/git/blobs", dict(encoding="base64", content=base64.b64encode(data).decode()))["sha"]
            entries.append(entry)
        paths = {e["path"] for e in entries}
        original = api(f"repos/{repo}/git/trees/{commit['tree']['sha']}?recursive=1")["tree"]
        for e in original:
            if e["type"] == "blob" and e["path"].startswith("src/") and e["path"] not in paths:
                entries.append(dict(path=e["path"], mode=e["mode"], type="blob", sha=None))
        version = ET.parse(ROOT / mod / "pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text
        snapshot = dict(source_repository="Voyage-for-the-ideal/aiplayspire", source_head=base,
                        source_directory=mod, mod_version=version, upstream=f"boardengineer/{mod}",
                        includes_uncommitted_sources=True, files=manifest)
        entries.append(dict(path="AIPLAYSpire-SNAPSHOT.json", mode="100644", type="blob", content=json.dumps(snapshot, indent=2) + "\n"))
        tree = api(f"repos/{repo}/git/trees", dict(base_tree=commit["tree"]["sha"], tree=entries))
        created = api(f"repos/{repo}/git/commits", dict(message="Archive AIPlaySpire combat mods before lightspeed migration", tree=tree["sha"], parents=[head]))
        api(f"repos/{repo}/git/refs", dict(ref="refs/heads/" + branch, sha=created["sha"]))
        remote = api(f"repos/{repo}/git/trees/{tree['sha']}?recursive=1")
        assert not remote.get("truncated")
        hashes = {e["path"]: e["sha"] for e in remote["tree"] if e["type"] == "blob"}
        for e in manifest:
            assert hashes[e["path"]] == e["git_blob"], e["path"]
        assert api(f"repos/{repo}/git/ref/heads/{branch}")["object"]["sha"] == created["sha"]
        records.append(dict(repository=repo, branch=branch, commit=created["sha"], files=len(manifest), version=version, source_head=base))
        (OUT / "archives.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(records[-1]), flush=True)
    worker = ROOT / "battle_engine/bin/battle-engine-worker.exe"
    if worker.exists():
        shutil.copyfile(worker, OUT / "battle-engine-worker-before.exe")
    run(["git", "switch", "-c", "codex/lightspeed-replacement"])


if __name__ == "__main__":
    main()
