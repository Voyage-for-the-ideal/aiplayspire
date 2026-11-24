#!/usr/bin/env bash
# One submission, two independent bounded lanes; no model-side polling.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
B="$(realpath -e "${1:?batch root}")"
P=/home/ubuntu/headless-spire-poc
case "$B" in
 "$P/runtime-acceptance-batch160-v1") PREFIX=headless-batch160; REPAIRED=0;;
 "$P/runtime-acceptance-batch160-v2") PREFIX=headless-batch160-r2; REPAIRED=1;;
 "$P/runtime-acceptance-batch160-v3") PREFIX=headless-batch160-r3; REPAIRED=2;;
 "$P/runtime-acceptance-batch160-v3-unfinished") PREFIX=headless-batch160-r3-unfinished; REPAIRED=3;;
 *) exit 78;;
esac
[[ ! -L "$1" ]] || exit 78
MODE="${2:---preflight}"
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
selection=(); scope='160 cases, seeds 5050201..5050360'
if [[ "$REPAIRED" = 3 ]]; then
 selection=(--selection "$B/selection.json")
 scope='63 unfinished seeds from the signed release1 report'
fi
case "$MODE" in --preflight|--submit|--supervise|--lane0|--lane1) ;; *) exit 64;; esac
if [[ "$MODE" == --lane* ]]; then
 LANE="${MODE#--lane}"; W="$B/lane$LANE"
 if [[ "$REPAIRED" != 0 ]]; then
  [[ -f "$B/SUBMITTED" && -f "$B/reports/repair-signoff.json" ]] || exit 78
  for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do [[ ! -e "$gate" && ! -L "$gate" ]]; done
  for name in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
   (set -o noclobber; printf 'limited to this repaired A10 lane\n' > "$W/$name")
  done
 fi
 export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
 unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
 cd "$W/saves"
 exec "$PY" -u -m "$(headless_module "$PYTHONPATH" batch160)" --lane "$LANE" --root "$W" \
  --launcher "$W/tools/start-session.sh" --native "$W/native-bin/battle-engine-worker" \
  --checkpoint "$W/value-format12.pth" --ascension 10 --max-actions 3000 --seed-base 5050201 "${selection[@]}" \
  >> "$W/reports/agent.log" 2>&1
fi
if [[ "$MODE" == --supervise ]]; then
 [[ $EUID -eq 0 ]] || exit 78
 # The supervisor only waits on systemd jobs; it never polls game state.
 units=("$PREFIX-lane0.service" "$PREFIX-lane1.service")
 pids=(); codes=(130 130); stop_requested=0
 finalize() {
  initial_code=$?
  trap - EXIT TERM INT HUP
  set +e
  systemctl stop "${units[@]}" 2>/dev/null
  gates_closed=true
  for lane in 0 1; do
   # A successful transient unit may already have been garbage-collected.
   # --wait's exit code/log and our recorded cgroup samples remain authoritative.
   systemctl show "${units[$lane]}" -p Id -p LoadState -p MainPID -p ActiveState -p Result -p ExecMainStatus \
    -p MemoryPeak -p MemorySwapPeak -p CPUUsageNSec > "$B/reports/lane$lane-systemd-final.txt" 2>&1
   W="$B/lane$lane"; mkdir -p "$W/reports/permit-archive"
   for name in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
    if [[ -f "$W/$name" && ! -L "$W/$name" ]]; then
     mv -- "$W/$name" "$W/reports/permit-archive/$name.batch-finished" || gates_closed=false
    fi
    [[ ! -e "$W/$name" && ! -L "$W/$name" ]] || gates_closed=false
   done
  done
  printf '{"lane_exit_codes":[%s,%s],"scoped_permits_closed":%s}\n' "${codes[0]}" "${codes[1]}" "$gates_closed" > "$B/reports/supervisor-result.json"
  "$PY" "$B/tools/audit-batch160.py" "$B" --verify > "$B/reports/preservation-audit.log" 2>&1
  audit_code=$?
  export PYTHONPATH="$B/lane0/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
  "$PY" -u -m "$(headless_module "$PYTHONPATH" batch160_report)" --root "$B" "${selection[@]}" > "$B/reports/report-generation.log" 2>&1
  report_code=$?
  if [[ "$REPAIRED" = 1 || "$REPAIRED" = 2 ]]; then
   "$PY" -m "$(headless_module "$PYTHONPATH" repair_batch_report)" --original "$P/runtime-acceptance-batch160-v1" --signoff "$B/reports/repair-signoff.json" --rerun "$B" > "$B/reports/repair-report.log" 2>&1 || report_code=1
  fi
  chown -R ubuntu:ubuntu "$B/reports" "$B/lane0/reports" "$B/lane1/reports"
  [[ $initial_code == 0 && $stop_requested == 0 && ${codes[0]} == 0 && ${codes[1]} == 0 && $audit_code == 0 && $report_code == 0 ]]
  exit $?
 }
 trap finalize EXIT
 trap 'stop_requested=1; systemctl stop "${units[@]}" 2>/dev/null || true' TERM INT HUP
 for lane in 0 1; do
  [[ $stop_requested == 0 ]] || break
  W="$B/lane$lane"
  systemd-run --unit="$PREFIX-lane$lane" --wait \
   -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/saves" \
   -p CPUQuota=100% -p MemoryHigh=768M -p MemoryMax=1024M -p MemorySwapMax=0 \
   -p TasksMax=96 -p RuntimeMaxSec=14400 -p TimeoutStopSec=30 -p KillMode=control-group \
   -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
   -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$W" \
   -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
   /bin/bash "$B/tools/run-batch160.sh" "$B" "--lane$lane" \
   > "$B/reports/lane$lane-systemd.log" 2>&1 &
  pids+=("$!")
 done
 for index in "${!pids[@]}"; do
  if wait "${pids[$index]}"; then codes[$index]=0; else codes[$index]=$?; fi
 done
 exit "$stop_requested"
fi
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
 [[ ! -e "$gate" && ! -L "$gate" ]] || exit 78
done
for unit in headless-collection-acceptance headless-multiseed-acceptance headless-batch24-supervisor headless-batch24-lane0 headless-batch24-lane1 headless-batch24-v2-supervisor headless-batch24-v2-pilot headless-batch24-v2-lane0 headless-batch24-v2-lane1 headless-orrery-live-confirmation headless-batch160-supervisor headless-batch160-lane0 headless-batch160-lane1; do
 ! systemctl is-active --quiet "$unit.service" || { echo "Already active: $unit" >&2; exit 78; }
done
[[ $(df --output=avail -k "$B" | tail -1) -gt 3145728 ]] || exit 78
if [[ "$REPAIRED" != 0 ]]; then
 ! systemctl list-units --state=running --no-legend 'headless-r2-pilot-*' | grep -q . || exit 78
 for unit in "$PREFIX-supervisor" "$PREFIX-lane0" "$PREFIX-lane1"; do ! systemctl is-active --quiet "$unit" || exit 78; done
 if [[ "$REPAIRED" = 2 || "$REPAIRED" = 3 ]]; then
  PYTHONPATH="$B/lane0/repo" "$PY" "$B/tools/stage-release2.py" --verify "$B"
 else
  PYTHONPATH="$B/lane0/repo" "$PY" "$B/tools/stage-repaired-batch160.py" --verify "$B"
 fi
fi
for lane in 0 1; do
 W="$B/lane$lane"
 for gate in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
  if [[ "$REPAIRED" != 0 ]]; then [[ ! -e "$W/$gate" && ! -L "$W/$gate" ]] || exit 78
  else [[ -f "$W/$gate" && ! -L "$W/$gate" ]] || exit 78; fi
 done
 "$PY" "$W/tools/check-profile.py" "$W"
done
"$PY" "$B/tools/audit-batch160.py" "$B" --preflight
if [[ "$MODE" == --preflight ]]; then
 echo "READY: $scope; two A10 lanes; difference aborts only its case; no game started"
 exit 0
fi
# An exclusive submission marker prevents accidental whole-plan resubmission.
sudo -n true
(set -o noclobber; printf '%s, submitted %s\n' "$scope" "$(date -u +%FT%TZ)" > "$B/SUBMITTED") || exit 78
exec sudo -n systemd-run --unit="$PREFIX-supervisor" \
 -p User=root -p WorkingDirectory="$B" -p MemoryMax=768M -p MemorySwapMax=0 -p CPUQuota=100% \
 -p TasksMax=64 -p RuntimeMaxSec=15600 -p TimeoutStopSec=300 \
 -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$B" \
 -p PrivateTmp=yes -p PrivateDevices=yes -p NoNewPrivileges=yes \
 /bin/bash "$B/tools/run-batch160.sh" "$B" --supervise
