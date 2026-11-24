#!/usr/bin/env bash
# Operator entry point for a frozen build. Nothing in the original batch is writable.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
W=$P/runtime-acceptance-batch160-repairs-v2
OLD=$P/runtime-acceptance-batch160-v1
PREVIOUS=$P/runtime-acceptance-batch160-repairs-v1/reports/repair-audit.json
NEW=$P/runtime-acceptance-batch160-v2
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
TAG="${1:?immutable build tag}"
[[ "$TAG" =~ ^[a-z0-9-]{1,30}$ && ! -e "$NEW" && ! -L "$NEW" ]]
[[ "$(realpath -e "$0")" == "$W/tools/run-repair-signoff.sh" ]]
cd "$W/repo"
export PYTHONPATH="$PWD" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
export HEADLESS_IMPORT_PREFIX="$(headless_prefix "$PYTHONPATH")"
MODE="${2:---prepare}"
[[ "$MODE" = --prepare || "$MODE" = --submit-after-pass ]]
for path in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do
 [[ ! -e "$path" && ! -L "$path" ]]
done
WITNESS="$W/reports/signoff-$TAG"
mkdir "$WITNESS" # acquiring this tag is exclusive
exec > "$WITNESS/job.log" 2>&1
"$PY" - "$W" "$WITNESS" <<'PY'
from importlib import import_module
from os import environ
prefix = environ["HEADLESS_IMPORT_PREFIX"]
import json,sys
from pathlib import Path
release_digest = import_module(prefix+"repair_signoff").release_digest
TARGETS = import_module(prefix+"repair_four").TARGETS
digest = import_module(prefix+"repair_ledger").digest
w,out=map(Path,sys.argv[1:])
proofs={}
for r in sorted(w.glob('pilot-stage8-*/reports/pilot-*.json')):
    d=json.loads(r.read_bytes());seed=d['seed']
    assert seed in TARGETS and seed not in proofs and d['status']=='original_stop_crossed'
    assert d['four_picker']['identical_rng_streams']==14 and d['replay']['replay_verified']
    assert not any((r.parent.parent/k).exists() for k in ('ALLOW_COLLECTION','ALLOW_RUN_SMOKE'))
    proofs[seed]=digest(r)
assert set(proofs)==set(TARGETS)
(out/'frozen.json').write_text(json.dumps(dict(four_original_receipts=proofs,release_sha256=release_digest(w))))
PY
"$PY" "$OLD/tools/audit-batch160.py" "$OLD" --preflight > "$WITNESS/preservation-start.json"
"$PY" "$W/tools/verify-repair-suites.py" --root "$W" --name "signoff-$TAG"
"$PY" "$W/tools/verify-batch160-repairs.py" --batch "$OLD" --worker "$W/native-bin/battle-engine-worker" --output "$WITNESS/previous26.json"
"$PY" -m "$(headless_module "$PYTHONPATH" repair_ledger)" --batch "$OLD" --previous "$PREVIOUS" --worker "$W/native-bin/battle-engine-worker" --output "$WITNESS/recorded.json"
# Freeze the original case list (29 with no after + every archived action landing in a picker).
"$PY" - "$WITNESS" "$OLD" "$PREVIOUS" <<'PY'
from importlib import import_module
from os import environ
prefix = environ["HEADLESS_IMPORT_PREFIX"]
from pathlib import Path
inventory = import_module(prefix+"repair_ledger").inventory
needs_live = import_module(prefix+"repair_signoff").needs_live
import json,sys
out,batch,old=map(Path,sys.argv[1:])
rows=json.loads((out/'recorded.json').read_bytes())
assert rows['counts']=={'recorded_step_matches':73,'requires_original_seed':29}
cases=[c for c,d in inventory(batch,old) if needs_live(c,d)]
assert len(cases)==37 and len({c['seed'] for c in cases})==37
for lane in (0,1):
    (out/f'seeds-{lane}').write_text(' '.join(str(c['seed']) for c in cases[lane::2]))
PY
for lane in 0 1; do
 D="$W/pilot-signoff-$TAG-$lane"
 mkdir "$D"
 cp -a --reflink=auto "$W"/{repo,tools,classes,native-bin,value-format12.pth,profile-baseline,profile-baseline-manifest.json} "$D/"
done
units=();jobs=()
for lane in 0 1; do units+=("headless-r2-proof-$TAG-$lane.service"); done
trap 'sudo -n systemctl stop "${units[@]}" 2>/dev/null || true; exit 1' INT TERM HUP
for lane in 0 1; do
 D="$W/pilot-signoff-$TAG-$lane"
 read -r -a seeds <<< "$(cat "$WITNESS/seeds-$lane")"
 sudo -n systemd-run --collect --wait --unit="${units[$lane]}" -p User=ubuntu -p CPUQuota=100% -p MemoryMax=1G -p MemorySwapMax=0 -p RuntimeMaxSec=5400 -p TasksMax=96 -p PrivateNetwork=yes -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$D" -p NoNewPrivileges=yes -p "ExecStopPost=/bin/rm -f -- $D/ALLOW_COLLECTION $D/ALLOW_RUN_SMOKE" /bin/bash "$D/tools/run-repair-pilot.sh" "$D" "${seeds[@]}" > "$WITNESS/systemd-$lane.log" 2>&1 &
 jobs+=("$!")
done
status=0
for pid in "${jobs[@]}"; do wait "$pid" || status=1; done
[[ "$status" = 0 ]]
"$PY" -m "$(headless_module "$PYTHONPATH" repair_signoff)" --root "$W" --batch "$OLD" --previous "$PREVIOUS" --prior26 "$WITNESS/previous26.json" --regressions "$W/reports/suites-signoff-$TAG.json" --frozen "$WITNESS/frozen.json" --out "$W/reports/repair-signoff-$TAG.json" --pilot "$W/pilot-signoff-$TAG-0" --pilot "$W/pilot-signoff-$TAG-1"
"$PY" "$OLD/tools/audit-batch160.py" "$OLD" --preflight > "$WITNESS/preservation-finish.json"
if [[ "$MODE" = --submit-after-pass ]]; then
 "$PY" "$W/tools/stage-repaired-batch160.py" --stage "$W/reports/repair-signoff-$TAG.json"
 /bin/bash "$NEW/tools/run-batch160.sh" "$NEW" --submit
fi
