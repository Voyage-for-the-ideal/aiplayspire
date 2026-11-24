#!/usr/bin/env bash
# Confirm all 29 original prefixes before any fresh retry; do not touch old gates.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
C=$P/runtime-acceptance-batch-r5-candidate
B=$P/runtime-acceptance-batch-r5-retry29
S=$P/runtime-acceptance-batch63-r4-followup
AUDIT=$P/runtime-acceptance-batch160-v3-unfinished
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
U=headless-batch-r5-followup29
mode="${1:---submit}"
[[ "$(realpath -e "$0")" == "$C/tools/run-followup29.sh" && ! -L "$C" && ! -L "$B" ]]
export PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
for f in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION "$C/ALLOW_COLLECTION" "$C/ALLOW_RUN_SMOKE"; do
 [[ ! -e "$f" && ! -L "$f" ]]
done

if [[ "$mode" == --pilot || "$mode" == --retry ]]; then
 lane="${2:?lane}"; [[ "$lane" =~ ^[01]$ && -f "$C/SIGNOFF_SUBMITTED" ]]
 if [[ "$mode" == --pilot ]]; then W="$C/pilot$lane"; mkdir "$W/reports"
 else W="$B/lane$lane"; [[ -f "$B/SUBMITTED" && -f "$B/reports/signoff29.json" ]]; fi
 for gate in "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do [[ ! -e "$gate" && ! -L "$gate" ]]; done
 trap 'rm -f -- "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"' EXIT
 (set -o noclobber; printf 'finite isolated A10 verification\n' > "$W/ALLOW_RUN_SMOKE"; printf 'evaluation only\n' > "$W/ALLOW_COLLECTION")
 export PYTHONPATH="$W/repo"
 unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
 if [[ "$mode" == --pilot ]]; then
  "$PY" -u -m "$(headless_module "$PYTHONPATH" repair_followup29)" pilot --source "$S" --root "$W" --lane "$lane" > "$W/reports/pilot.log" 2>&1
 else
  cd "$W/saves"
  "$PY" -u -m "$(headless_module "$PYTHONPATH" batch160)" --root "$W" --lane "$lane" --schedule "$B/schedule.json" \
   --launcher "$W/tools/start-session.sh" --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" \
   --ascension 10 --max-actions 3000 > "$W/reports/agent.log" 2>&1
 fi
 exit
fi

cd "$C/repo"
export PYTHONPATH="$PWD"
audit() { "$PY" "$AUDIT/tools/audit-batch160.py" "$AUDIT" --preflight; }
if [[ "$mode" == --submit ]]; then
 [[ $EUID -ne 0 && ! -e "$C/SIGNOFF_SUBMITTED" && ! -L "$C/SIGNOFF_SUBMITTED" && ! -e "$B" ]]
 ! systemctl list-units --state=running --no-legend 'headless-batch*' | grep -q .
 [[ $(df --output=avail -k "$C" | tail -1) -gt 3145728 ]]
 audit > "$C/reports/preservation-before.json"
 "$PY" -m "$(headless_module "$PYTHONPATH" repair_followup29)" plan --source "$S" > "$C/reports/plan29.json"
 "$PY" "$C/tools/check-profile.py" "$C"
 original="$S/recheck44/lane0"
 for f in value-format12.pth profile-baseline-manifest.json repo/battle_engine/src/TurnSearch.cpp; do
  cmp -s "$original/$f" "$C/$f"
 done
 for name in policy agent; do
  original_path=$(headless_path "$original/repo" "$name")
  candidate_path=$(headless_path "$C/repo" "$name")
  cmp -s "$original/repo/$original_path" "$C/repo/$candidate_path"
 done
 diff -qr --exclude=__pycache__ "$original/repo/sts_ai_framework/routing" "$C/repo/sts_ai_framework/routing"
 sudo -n true
 (set -o noclobber; date -u +%FT%TZ > "$C/SIGNOFF_SUBMITTED")
 exec sudo -n systemd-run --unit="$U" -p User=root -p CPUQuota=100% -p MemoryMax=768M -p MemorySwapMax=0 \
  -p RuntimeMaxSec=14400 -p TasksMax=72 -p TimeoutStopSec=90 -p ProtectSystem=strict -p ProtectHome=read-only \
  -p ReadWritePaths="$P" -p "ReadOnlyPaths=$S $AUDIT $P/runtime-acceptance-batch160-repairs-r4-targeted" \
  -p PrivateTmp=yes -p PrivateDevices=yes /bin/bash "$0" --supervise
fi

[[ "$mode" == --supervise && $EUID -eq 0 && -f "$C/SIGNOFF_SUBMITTED" && ! -e "$B" ]]
exec > "$C/reports/job.log" 2>&1
as_user() { runuser -u ubuntu -- "$@"; }
units=()
trap 'if (( ${#units[@]} )); then systemctl stop "${units[@]}" 2>/dev/null || true; fi; exit 1' INT TERM HUP
release=$(as_user "$PY" -c 'import sys; from importlib import import_module; from pathlib import Path; print(import_module(sys.argv[1]).release_digest(Path(sys.argv[2])))' "$(headless_module "$PYTHONPATH" repair_signoff)" "$C")

run_lanes() {
 local phase=$1 logs=$2 W lane pid
 local -a pids=()
 codes=(); units=(); closed=true
 for lane in 0 1; do
  if [[ "$phase" == pilot ]]; then W="$C/pilot$lane"; else W="$B/lane$lane"; fi
  units+=("$U-$phase-$lane.service")
  systemd-run --unit="$U-$phase-$lane" --wait --collect -p User=ubuntu -p CPUQuota=100% \
   -p MemoryMax=1G -p MemorySwapMax=0 -p TasksMax=96 -p RuntimeMaxSec=5400 -p TimeoutStopSec=30 \
   -p PrivateNetwork=yes -p PrivateTmp=yes -p PrivateDevices=yes -p ProtectSystem=strict -p ProtectHome=read-only \
   -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
   -p "ExecStopPost=/bin/rm -f -- $W/ALLOW_COLLECTION $W/ALLOW_RUN_SMOKE" \
   /bin/bash "$0" "--$phase" "$lane" > "$logs/$phase-$lane-systemd.log" 2>&1 &
  pids+=("$!")
 done
 for pid in "${pids[@]}"; do if wait "$pid"; then codes+=(0); else codes+=("$?"); fi; done
 units=()
 for lane in 0 1; do
  if [[ "$phase" == pilot ]]; then W="$C/pilot$lane"; else W="$B/lane$lane"; fi
  for gate in "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do
   if [[ -e "$gate" || -L "$gate" ]]; then closed=false; fi
  done
 done
 printf '{"lane_exit_codes":[%s,%s],"scoped_permits_closed":%s}\n' "${codes[0]}" "${codes[1]}" "$closed" > "$logs/$phase-supervisor-result.json"
}

for lane in 0 1; do
 W="$C/pilot$lane"
 as_user mkdir "$W"
 as_user cp -a --reflink=auto "$C"/{repo,tools,classes,native-bin,value-format12.pth,profile-baseline,profile-baseline-manifest.json} "$W/"
done
run_lanes pilot "$C/reports"
as_user "$PY" -m "$(headless_module "$PYTHONPATH" repair_followup29)" verify --source "$S" --root "$C" --release "$release"
[[ "${codes[*]}" == '0 0' && "$closed" == true ]]
audit > "$C/reports/preservation-after-pilot.json"
as_user "$PY" -m "$(headless_module "$PYTHONPATH" repair_followup29)" stage --source "$S" --root "$C" --target "$B"
as_user "$PY" -m "$(headless_module "$PYTHONPATH" repair_followup29)" verify-stage --target "$B"
(set -o noclobber; printf 'A10: 29 original seeds; %s\n' "$(date -u +%FT%TZ)" > "$B/SUBMITTED")
run_lanes retry "$B/reports"
cp "$B/reports/retry-supervisor-result.json" "$B/reports/supervisor-result.json"
audit > "$B/reports/preservation-report.json" || true
as_user "$PY" -m "$(headless_module "$PYTHONPATH" repair_followup29)" verify-stage --target "$B"
env PYTHONPATH="$B/lane0/repo" "$PY" -u -m "$(headless_module "$B/lane0/repo" batch160_report)" \
 --root "$B" --schedule "$B/schedule.json" | tee "$B/reports/report-generation.log"
"$PY" - "$B/reports/retry29-report.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1],encoding='utf8'))
assert r['independently_replayed']==r['attempted']==r['planned']==29
PY
