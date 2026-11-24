#!/usr/bin/env bash
# Original-case check first; new-seed coverage starts after its report is sealed.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
B=$P/runtime-acceptance-batch63-r4-followup
S=$P/runtime-acceptance-batch160-v3-unfinished
R=$P/runtime-acceptance-batch160-repairs-r4-targeted
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
UNIT=headless-batch63-r4-followup
MODE="${1:---submit}"
[[ "$(realpath -e "$0")" == "$B/tools/run-followup63.sh" && ! -L "$B" ]]
export PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do [[ ! -e "$gate" && ! -L "$gate" ]]; done

if [[ "$MODE" == --lane ]]; then
 scope="${2:?cohort}"; lane="${3:?lane}"
 [[ -f "$B/SUBMITTED" && "$scope" =~ ^(recheck44|fresh40)$ && "$lane" =~ ^[01]$ ]]
 W="$B/$scope/lane$lane"
 for gate in "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do [[ ! -e "$gate" && ! -L "$gate" ]]; done
 trap 'rm -f -- "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"' EXIT
 (set -o noclobber; printf 'finite isolated A10 evaluation\n' > "$W/ALLOW_RUN_SMOKE"; printf 'evaluation traces only\n' > "$W/ALLOW_COLLECTION")
 export PYTHONPATH="$W/repo"
 unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
 cd "$W/saves"
 "$PY" -u -m "$(headless_module "$PYTHONPATH" batch160)" --root "$W" --lane "$lane" --schedule "$B/$scope/schedule.json" \
  --launcher "$W/tools/start-session.sh" --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" \
  --ascension 10 --max-actions 3000 \
  > "$W/reports/agent.log" 2>&1
 exit
fi

check() { env PYTHONPATH="$R/repo" "$PY" "$B/tools/prepare-followup63.py" verify; }
audit() { "$PY" "$S/tools/audit-batch160.py" "$S" --preflight; }
if [[ "$MODE" == --submit ]]; then
 [[ $EUID -ne 0 && ! -e "$B/SUBMITTED" && ! -L "$B/SUBMITTED" ]]
 ! systemctl list-units --state=running --no-legend 'headless-batch*' | grep -q .
 [[ $(df --output=avail -k "$B" | tail -1) -gt 3145728 ]]
 check
 for scope in recheck44 fresh40; do
  for lane in 0 1; do
   W="$B/$scope/lane$lane"; "$PY" "$W/tools/check-profile.py" "$W"
   [[ ! -e "$W/ALLOW_COLLECTION" && ! -L "$W/ALLOW_COLLECTION" && ! -e "$W/ALLOW_RUN_SMOKE" && ! -L "$W/ALLOW_RUN_SMOKE" ]]
  done
 done
 audit > "$B/reports/preservation-before.json"
 sudo -n true
 (set -o noclobber; printf 'A10: original 44, then fresh 40; %s\n' "$(date -u +%FT%TZ)" > "$B/SUBMITTED")
 exec sudo -n systemd-run --unit="$UNIT" -p User=root -p CPUQuota=100% -p MemoryMax=768M -p MemorySwapMax=0 \
  -p RuntimeMaxSec=10800 -p TasksMax=64 -p TimeoutStopSec=90 -p ProtectSystem=strict -p ProtectHome=read-only \
  -p ReadWritePaths="$B" -p PrivateTmp=yes -p PrivateDevices=yes /bin/bash "$0" --supervise
fi

[[ "$MODE" == --supervise && $EUID -eq 0 && -f "$B/SUBMITTED" ]]
exec > "$B/reports/job.log" 2>&1
units=()
trap 'if (( ${#units[@]} )); then systemctl stop "${units[@]}" 2>/dev/null || true; fi; exit 1' INT TERM HUP
for scope in recheck44 fresh40; do
 check
 phase="$B/$scope"; units=(); pids=(); codes=(); closed=true
 for lane in 0 1; do
  W="$phase/lane$lane"; units+=("$UNIT-$scope-$lane.service")
  systemd-run --unit="$UNIT-$scope-$lane" --wait --collect -p User=ubuntu -p CPUQuota=100% \
   -p MemoryMax=1G -p MemorySwapMax=0 -p TasksMax=96 -p RuntimeMaxSec=5400 -p TimeoutStopSec=30 \
   -p PrivateNetwork=yes -p PrivateTmp=yes -p PrivateDevices=yes -p ProtectSystem=strict -p ProtectHome=read-only \
   -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
   -p "ExecStopPost=/bin/rm -f -- $W/ALLOW_COLLECTION $W/ALLOW_RUN_SMOKE" \
   /bin/bash "$0" --lane "$scope" "$lane" > "$phase/reports/lane$lane-systemd.log" 2>&1 &
  pids+=("$!")
 done
 for pid in "${pids[@]}"; do if wait "$pid"; then codes+=(0); else codes+=("$?"); fi; done
 units=()
 for lane in 0 1; do
  W="$phase/lane$lane"
  for gate in "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do
   if [[ -e "$gate" || -L "$gate" ]]; then closed=false; fi
  done
 done
 printf '{"lane_exit_codes":[%s,%s],"scoped_permits_closed":%s}\n' "${codes[0]}" "${codes[1]}" "$closed" > "$phase/reports/supervisor-result.json"
 audit > "$phase/reports/preservation-report.json" || true
 check
 env PYTHONPATH="$phase/lane0/repo" "$PY" -u -m "$(headless_module "$phase/lane0/repo" batch160_report)" \
  --root "$phase" --schedule "$phase/schedule.json" | tee "$phase/reports/report-generation.log"
 # Gameplay differences are not operational failures. Missing evidence,
 # failing audit or incomplete lanes, however, do not release the next phase.
 "$PY" - "$phase/reports/$scope-report.json" <<'PY'
import json,sys
r=json.load(open(sys.argv[1],encoding='utf8'))
assert r['independently_replayed']==r['attempted']==r['planned']
assert r['status'] in ('passed','finished_with_differences')
PY
done
