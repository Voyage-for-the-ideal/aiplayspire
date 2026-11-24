#!/usr/bin/env bash
# One detached verification job. A failed case does not stop its pilot lane;
# only complete evidence and regressions permit a new batch submission.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
W=$P/runtime-acceptance-batch160-repairs-v3
SOURCE=$P/runtime-acceptance-batch160-v2
OLD=$P/runtime-acceptance-batch160-v1
NEW=$P/runtime-acceptance-batch160-v3
PREVIOUS=$P/runtime-acceptance-batch160-repairs-v1/reports/repair-audit.json
EVIDENCE=$W/evidence/release1-outcomes.json
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
UNIT=headless-batch160-r3-signoff
stage_args=()
read_only="$SOURCE $OLD $P/runtime-acceptance-batch160-repairs-v1 $P/runtime-acceptance-batch160-repairs-v2"
if [[ "$(realpath -e "$0")" = "$W-unfinished/tools/run-release2-signoff.sh" ]]; then
 read_only+=" $W"
 W="$W-unfinished"; NEW="$NEW-unfinished"; UNIT="$UNIT-unfinished"
 EVIDENCE="$W/evidence/release1-outcomes.json"
 stage_args=(--unfinished)
fi
[[ "$(realpath -e "$0")" = "$W/tools/run-release2-signoff.sh" && ! -L "$W" && ! -e "$NEW" && ! -L "$NEW" ]]
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do
 [[ ! -e "$gate" && ! -L "$gate" ]]
done
cd "$W/repo"
export PYTHONPATH="$PWD" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
export HEADLESS_IMPORT_PREFIX="$(headless_prefix "$PYTHONPATH")"
export HEADLESS_ORIGINAL_160_BATCH="$OLD" HEADLESS_RELEASE2_SOURCE_BATCH="$SOURCE" HEADLESS_RELEASE2_SOURCE_REPORT="$EVIDENCE"
if [[ "${1:---submit}" = --submit ]]; then
 [[ $EUID != 0 && ! -e "$W/SIGNOFF_SUBMITTED" && ! -L "$W/SIGNOFF_SUBMITTED" ]]
 for old in headless-batch160-supervisor headless-batch160-r2-supervisor headless-batch160-r3-supervisor headless-batch160-r3-signoff headless-batch160-r3-unfinished-supervisor; do
  ! systemctl is-active --quiet "$old"
 done
 "$PY" "$SOURCE/tools/audit-batch160.py" "$SOURCE" --preflight > "$W/reports/preservation-before.json"
 "$PY" -m "$(headless_module "$PYTHONPATH" release2)" plan --batch "$SOURCE" --source "$EVIDENCE" > "$W/reports/plan"
 (set -o noclobber; date -u +%FT%TZ > "$W/SIGNOFF_SUBMITTED")
 exec sudo -n systemd-run --unit="$UNIT" -p User=root -p CPUQuota=100% -p MemoryMax=768M -p MemorySwapMax=0 \
  -p RuntimeMaxSec=18000 -p TasksMax=96 -p ProtectSystem=strict -p ProtectHome=read-only \
  -p ReadWritePaths="$P" -p ReadOnlyPaths="$read_only" -p PrivateTmp=yes \
  /bin/bash "$0" --supervise
fi
[[ "$1" = --supervise && $EUID = 0 && -f "$W/SIGNOFF_SUBMITTED" ]]
exec > "$W/reports/signoff-job.log" 2>&1
as_user() { runuser -u ubuntu -- "$@"; }
release=$(as_user "$PY" -c 'import sys; from pathlib import Path; from importlib import import_module; from os import environ; print(import_module(environ["HEADLESS_IMPORT_PREFIX"]+"repair_signoff").release_digest(Path(sys.argv[1])))' "$W")
units=()
trap 'if (( ${#units[@]} )); then systemctl stop "${units[@]}" 2>/dev/null || true; fi; exit 1' INT TERM HUP

run_lanes() {
 local kind=$1 runner=$2 pattern=$3 lane D i pid status=0
 local -a jobs=() arranged=() seeds=()
 read -r -a arranged < "$pattern"
 for lane in 0 1; do
  D="$W/pilot-$kind-$lane"
  as_user mkdir "$D" || return 1
  as_user cp -a --reflink=auto "$W"/{repo,tools,classes,native-bin,value-format12.pth,profile-baseline,profile-baseline-manifest.json} "$D/" || return 1
  seeds=()
  for ((i=lane;i<${#arranged[@]};i+=2)); do seeds+=("${arranged[i]}"); done
  units+=("$UNIT-$kind-$lane.service")
  systemd-run --collect --wait --unit="$UNIT-$kind-$lane" -p User=ubuntu \
   -p CPUQuota=100% -p MemoryMax=1G -p MemorySwapMax=0 -p RuntimeMaxSec=7200 -p TasksMax=96 \
   -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
   -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$D" -p NoNewPrivileges=yes \
   -p "ExecStopPost=/bin/rm -f -- $D/ALLOW_COLLECTION $D/ALLOW_RUN_SMOKE" \
   /bin/bash "$D/tools/$runner" "$D" "${seeds[@]}" > "$W/reports/systemd-$kind-$lane.log" 2>&1 &
  jobs+=("$!")
 done
 for pid in "${jobs[@]}"; do wait "$pid" || status=1; done
 units=()
 return "$status"
}

# Publish a per-case ledger even if a lane timed out. Incomplete evidence is
# never treated as a successful signoff.
first=0
run_lanes new run-release2-pilot.sh "$W/reports/plan" || first=1
as_user "$PY" -m "$(headless_module "$PYTHONPATH" release2)" verify --batch "$SOURCE" --source "$EVIDENCE" \
 --root "$W" --pilots "$W/pilot-new-0" "$W/pilot-new-1" --output "$W/reports/release2-signoff.json" --release "$release"
[[ "$first" = 0 ]]
# Only now freeze and regress all 102 + 26 cases on the same inputs.
as_user "$PY" - "$W" "$release" <<'PY'
from importlib import import_module
from os import environ
prefix = environ["HEADLESS_IMPORT_PREFIX"]
import sys
from pathlib import Path
save_json = import_module(prefix+"agent").save_json
release_digest = import_module(prefix+"repair_signoff").release_digest
root, expected = Path(sys.argv[1]), sys.argv[2]
assert release_digest(root) == expected
save_json(root/'reports/frozen.json', {'release_sha256':expected})
PY
as_user "$PY" "$W/tools/verify-repair-suites.py" --root "$W" --name release2
as_user "$PY" "$W/tools/verify-batch160-repairs.py" --batch "$OLD" --worker "$W/native-bin/battle-engine-worker" --output "$W/reports/previous26.json"
as_user "$PY" - "$OLD" "$PREVIOUS" <<'PY' > "$W/reports/prior-plan"
from importlib import import_module
from os import environ
prefix = environ["HEADLESS_IMPORT_PREFIX"]
from pathlib import Path
import sys
inventory = import_module(prefix+"repair_ledger").inventory
needs_live = import_module(prefix+"repair_signoff").needs_live
plan = [c['seed'] for c,d in inventory(*map(Path,sys.argv[1:])) if needs_live(c,d)]
assert len(plan) == len(set(plan)) == 37
print(*plan)
PY
run_lanes prior run-repair-pilot.sh "$W/reports/prior-plan"
as_user "$PY" -m "$(headless_module "$PYTHONPATH" repair_signoff)" --root "$W" --batch "$OLD" --previous "$PREVIOUS" \
 --prior26 "$W/reports/previous26.json" --regressions "$W/reports/suites-release2.json" --frozen "$W/reports/frozen.json" \
 --out "$W/reports/repair-signoff.json" --pilot "$W/pilot-prior-0" --pilot "$W/pilot-prior-1"
as_user "$PY" "$SOURCE/tools/audit-batch160.py" "$SOURCE" --preflight > "$W/reports/preservation-after.json"
as_user "$PY" "$W/tools/stage-release2.py" --stage "${stage_args[@]}"
as_user /bin/bash "$NEW/tools/run-batch160.sh" "$NEW" --submit
