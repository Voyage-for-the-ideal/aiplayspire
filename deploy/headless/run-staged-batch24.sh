#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
B="$(realpath -e "${1:?batch root}")"
P=/home/ubuntu/headless-spire-poc
[[ "$B" == "$P/runtime-acceptance-batch24-v2" ]] || exit 78
MODE="${2:---preflight}"
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
PREFIX=headless-batch24-v2
case "$MODE" in --preflight|--submit|--supervise|--pilot|--lane0|--lane1) ;; *) exit 64;; esac
if [[ "$MODE" == --pilot || "$MODE" == --lane* ]]; then
 NAME="${MODE#--}";W="$B/$NAME"
 export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
 unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
 cd "$W/saves"
 if [[ "$NAME" == pilot ]]; then
  exec "$PY" -u -m "$(headless_module "$PYTHONPATH" live_confirmation)" --batch "$B" >> "$W/reports/agent.log" 2>&1
 fi
 # The full lanes need the independent five-target proof, not just a supervisor ACK.
 "$PY" - "$B" "$W" <<'PY'
import json,sys,hashlib
from pathlib import Path
b=Path(sys.argv[1]);g=json.loads((b/'reports/pilot-gate.json').read_text())
assert g['passed'] is True and len(g['proofs'])==5 and all(p['passed'] for p in g['proofs'])
assert hashlib.sha256((b/'pilot/reports/confirmation-status.json').read_bytes()).hexdigest()==g['confirmation_status_sha256']
assert hashlib.sha256((Path(sys.argv[2])/'native-bin/battle-engine-worker').read_bytes()).hexdigest()==g['native_sha256']
PY
 exec "$PY" -u -m "$(headless_module "$PYTHONPATH" batch)" --lane "${NAME#lane}" --root "$W" \
  --launcher "$W/tools/start-session.sh" --native "$W/native-bin/battle-engine-worker" \
  --checkpoint "$W/value-format12.pth" --ascension 10 --max-actions 3000 --seed-base 5050101 >> "$W/reports/agent.log" 2>&1
fi
if [[ "$MODE" == --supervise ]]; then
 [[ $EUID == 0 ]] || exit 78
 units=("$PREFIX-pilot.service" "$PREFIX-lane0.service" "$PREFIX-lane1.service")
 stop_requested=0;stage2_started=false;pilot_code=130;gate_code=130;codes=(130 130);pids=()
 close_gates() {
  local W="$B/$1";mkdir -p "$W/reports/permit-archive"
  for name in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
   if [[ -f "$W/$name" && ! -L "$W/$name" ]]; then
    mv -- "$W/$name" "$W/reports/permit-archive/$name.batch-finished" || gates_closed=false
   elif [[ ! -e "$W/reports/permit-archive/$name.batch-finished" ]]; then
    printf 'Conditional stage was not opened\n' > "$W/reports/permit-archive/$name.not-opened"
   fi
   [[ ! -e "$W/$name" && ! -L "$W/$name" ]] || gates_closed=false
  done
 }
 finalize() {
  initial_code=$?;trap - EXIT TERM INT HUP;set +e
  systemctl stop "${units[@]}" 2>/dev/null
  gates_closed=true
  for name in pilot lane0 lane1; do
   systemctl show "$PREFIX-$name.service" -p Id -p LoadState -p MainPID -p ActiveState -p Result -p ExecMainStatus \
    -p MemoryPeak -p MemorySwapPeak -p CPUUsageNSec > "$B/reports/$name-systemd-final.txt" 2>&1
   close_gates "$name"
  done
  printf '{"pilot_exit_code":%s,"gate_exit_code":%s,"stage2_started":%s,"lane_exit_codes":[%s,%s],"scoped_permits_closed":%s}\n' \
   "$pilot_code" "$gate_code" "$stage2_started" "${codes[0]}" "${codes[1]}" "$gates_closed" > "$B/reports/supervisor-result.json"
  "$PY" "$B/tools/audit-batch24.py" "$B" --verify > "$B/reports/preservation-audit.log" 2>&1;audit_code=$?
  export PYTHONPATH="$B/pilot/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
  "$PY" -u -m "$(headless_module "$PYTHONPATH" staged_report)" --batch "$B" > "$B/reports/report-generation.log" 2>&1;report_code=$?
  chown -R ubuntu:ubuntu "$B/reports" "$B/pilot/reports" "$B/lane0/reports" "$B/lane1/reports"
  [[ $initial_code == 0 && $stop_requested == 0 && $pilot_code == 0 && $gate_code == 0 && ${codes[0]} == 0 && ${codes[1]} == 0 && $audit_code == 0 && $report_code == 0 ]]
  exit $?
 }
 trap finalize EXIT
 trap 'stop_requested=1; systemctl stop "${units[@]}" 2>/dev/null || true' TERM INT HUP
 launch() {
  local NAME="$1";local LIMIT="$2";local W="$B/$NAME"
  systemd-run --unit="$PREFIX-$NAME" --wait -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/saves" \
   -p CPUQuota=100% -p MemoryHigh=768M -p MemoryMax=1024M -p MemorySwapMax=0 -p TasksMax=96 -p RuntimeMaxSec="$LIMIT" \
   -p TimeoutStopSec=30 -p KillMode=control-group -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
   -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
   /bin/bash "$B/tools/run-staged-batch24.sh" "$B" "--$NAME" > "$B/reports/$NAME-systemd.log" 2>&1
 }
 if launch pilot 1800;then pilot_code=0;else pilot_code=$?;fi
 gates_closed=true;close_gates pilot
 [[ $stop_requested == 0 ]] || exit 1
 export PYTHONPATH="$B/pilot/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
 if "$PY" -u -m "$(headless_module "$PYTHONPATH" live_confirmation)" --batch "$B" --verify > "$B/reports/pilot-gate.log" 2>&1;then gate_code=0;else gate_code=$?;fi
 [[ $stop_requested == 0 && $pilot_code == 0 && $gate_code == 0 ]] || exit 1
 for lane in 0 1;do
  [[ $stop_requested == 0 ]] || break
  for permit in ALLOW_RUN_SMOKE ALLOW_COLLECTION;do
   printf 'Five original-seed live targets independently verified; finite 12-case lane\n' > "$B/lane$lane/$permit"
   chown ubuntu:ubuntu "$B/lane$lane/$permit"
  done
 done
 [[ $stop_requested == 0 ]] || exit 1
 stage2_started=true
 for lane in 0 1;do
  [[ $stop_requested == 0 ]] || break
  launch "lane$lane" 7200 & pids+=("$!")
 done
 for index in "${!pids[@]}";do
  if wait "${pids[$index]}";then codes[$index]=0;else codes[$index]=$?;fi
 done
 exit "$stop_requested"
fi
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION;do [[ ! -e "$gate" && ! -L "$gate" ]] || exit 78;done
for unit in headless-collection-acceptance headless-multiseed-acceptance headless-batch24-supervisor headless-batch24-lane0 headless-batch24-lane1 \
 "$PREFIX-supervisor" "$PREFIX-pilot" "$PREFIX-lane0" "$PREFIX-lane1";do
 ! systemctl is-active --quiet "$unit.service" || exit 78
done
[[ $(df --output=avail -k "$B" | tail -1) -gt 3145728 ]] || exit 78
for permit in ALLOW_RUN_SMOKE ALLOW_COLLECTION;do [[ -f "$B/pilot/$permit" && ! -L "$B/pilot/$permit" ]] || exit 78;done
for name in lane0 lane1;do for permit in ALLOW_RUN_SMOKE ALLOW_COLLECTION;do [[ ! -e "$B/$name/$permit" && ! -L "$B/$name/$permit" ]] || exit 78;done;done
"$PY" "$B/tools/audit-batch24.py" "$B" --preflight
export PYTHONPATH="$B/pilot/repo" PYTHONDONTWRITEBYTECODE=1
export HEADLESS_IMPORT_PREFIX="$(headless_prefix "$PYTHONPATH")"
"$PY" - "$B" <<'PY'
from importlib import import_module
from os import environ
prefix = environ["HEADLESS_IMPORT_PREFIX"]
from pathlib import Path
import sys
evidence = import_module(prefix+"live_confirmation").evidence
assert len(evidence(Path(sys.argv[1])))==5
print('READY: five original-seed live confirmations -> independent replay gate -> two lanes x 12; no game started')
PY
[[ "$MODE" == --submit ]] || exit 0
sudo -n true
(set -o noclobber;date -u +%FT%TZ > "$B/SUBMITTED") || exit 78
exec sudo -n systemd-run --unit="$PREFIX-supervisor" -p User=root -p WorkingDirectory="$B" \
 -p MemoryMax=512M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 -p RuntimeMaxSec=10800 -p TimeoutStopSec=300 \
 -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$B" -p PrivateTmp=yes -p PrivateDevices=yes -p NoNewPrivileges=yes \
 /bin/bash "$B/tools/run-staged-batch24.sh" "$B" --supervise
