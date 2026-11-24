#!/usr/bin/env bash
# Finite server evaluation: two A10 lanes per phase, with fresh80 queued behind the retry.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
A=$P/runtime-acceptance-batch160-v3-unfinished
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
runner="$(realpath -e "$0")"
case "$runner" in
 "$P/runtime-acceptance-batch-r6-retry29-fixes/tools/run-retry13-fresh80.sh")
  C=$P/runtime-acceptance-batch-r6-retry29-fixes
  S=$P/runtime-acceptance-batch-r5-retry29
  B=$P/runtime-acceptance-batch-r6-retry13-fresh80
  U=headless-batch-r6-retry13-fresh80
  retry=retry13; Q="$C"; stage=(stage "$C" "$S" "$B")
  ;;
 "$P/runtime-acceptance-batch-r7-queue/tools/run-retry25-fresh80.sh")
  C=$P/runtime-acceptance-batch-r7-r6-differences
  S=$P/runtime-acceptance-batch-r6-retry13-fresh80
  B=$P/runtime-acceptance-batch-r7-retry25-fresh80
  U=headless-batch-r7-retry25-fresh80
  retry=retry25; Q=$P/runtime-acceptance-batch-r7-queue
  stage=(stage-r7 "$C" "$S" "$B" "$Q/repo")
  ;;
 "$P/runtime-acceptance-batch-r8-queue/tools/run-retry16-fresh80.sh")
  C=$P/runtime-acceptance-batch-r8-targeted.GUTma38z
  S=$P/runtime-acceptance-batch-r7-retry25-fresh80
  B=$P/runtime-acceptance-batch-r8-retry16-fresh80
  U=headless-batch-r8-retry16-fresh80
  retry=retry16; Q=$P/runtime-acceptance-batch-r8-queue
  stage=(stage-r8 "$C" "$S" "$B" "$Q/repo")
  ;;
 *) echo 'Expected a known isolated evaluation runner path' >&2; exit 78 ;;
esac
mode="${1:---submit}"
export PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
[[ ! -L "$B" && ! -L "$Q" ]]
for gate in "$P/ALLOW_RUN_SMOKE" "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
 [[ ! -e "$gate" && ! -L "$gate" ]]
done

if [[ "$mode" == --lane ]]; then
 scope="$2"; lane="$3"
 [[ ( "$scope" == "$retry" || "$scope" == fresh80 ) && "$lane" =~ ^[01]$ ]]
 F="$B/$scope"; W="$F/lane$lane"
 [[ -f "$F/SUBMITTED" && -f "$B/freeze.json" && ! -L "$W" ]]
 cd "$W/saves"
 exec >"$W/reports/agent.log" 2>&1
 export PYTHONPATH="$W/repo"
 for gate in "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do [[ ! -e "$gate" && ! -L "$gate" ]]; done
 trap 'rm -f -- "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"' EXIT
 (set -o noclobber; printf 'finite isolated A10 evaluation\n' > "$W/ALLOW_RUN_SMOKE"; printf 'evaluation only\n' > "$W/ALLOW_COLLECTION")
 "$PY" -u -m "$(headless_module "$PYTHONPATH" batch160)" --root "$W" --lane "$lane" --schedule "$F/schedule.json" \
  --launcher "$W/tools/start-session.sh" --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" \
  --ascension 10 --max-actions 3000
 exit
fi

export PYTHONPATH="$Q/repo"
audit() { "$PY" "$A/tools/audit-batch160.py" "$A" --preflight; }
if [[ "$mode" == --submit ]]; then
 [[ $EUID -ne 0 ]]
 exec 9>"$Q/reports/$retry-fresh80-submit.lock"
 flock -n 9
 if [[ -f "$B/SUBMITTED" ]]; then printf 'Already submitted: %s\n' "$B"; exit 0; fi
 [[ ! -e "$B" ]]
 running="$(systemctl list-units --type=service --state=active,activating,deactivating --no-legend 'headless-batch*' 'headless-r8-targeted*')"
 [[ -z "$running" ]] || { printf 'Another evaluation job already exists:\n%s\n' "$running"; exit 2; }
 [[ $(df --output=avail -k "$C" | tail -1) -gt 3145728 ]]
 sudo -n true
 audit >"$Q/reports/$retry-fresh80-preflight.json"
 "$PY" -m "$(headless_module "$PYTHONPATH" batch160_next)" "${stage[@]}"
 cp "$Q/reports/$retry-fresh80-preflight.json" "$B/reports/preservation-before.json"
 sudo -n systemd-run --no-block --collect --unit="$U" -p User=root -p CPUQuota=100% -p MemoryMax=768M \
  -p MemorySwapMax=0 -p RuntimeMaxSec=21600 -p TasksMax=72 -p TimeoutStopSec=60 \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$B" \
  -p PrivateTmp=yes -p PrivateDevices=yes /bin/bash "$runner" --supervise
 printf 'Queued: %s\n' "$B"
 exit
fi

[[ "$mode" == --supervise && $EUID -eq 0 && -f "$B/freeze.json" ]]
exec >"$B/reports/supervisor.log" 2>&1
(set -o noclobber; date -u +%FT%TZ >"$B/SUBMITTED")
units=()
trap 'if (( ${#units[@]} )); then systemctl stop "${units[@]}" 2>/dev/null || true; fi; exit 1' INT TERM HUP
as_user() { runuser -u ubuntu -- "$@"; }
for scope in "$retry" fresh80; do
 F="$B/$scope"
 as_user "$PY" -m "$(headless_module "$PYTHONPATH" batch160_next)" verify "$B" "$scope"
 audit >"$F/reports/preservation-before.json"
 (set -o noclobber; date -u +%FT%TZ >"$F/SUBMITTED")
 pids=(); codes=(); units=(); closed=true
 for lane in 0 1; do
  W="$F/lane$lane"; unit="$U-$scope-$lane"; units+=("$unit.service")
  systemd-run --unit="$unit" --wait --collect -p User=ubuntu -p CPUQuota=100% \
   -p MemoryMax=1G -p MemorySwapMax=0 -p TasksMax=96 -p RuntimeMaxSec=12600 -p TimeoutStopSec=30 \
   -p PrivateNetwork=yes -p PrivateTmp=yes -p PrivateDevices=yes -p ProtectSystem=strict -p ProtectHome=read-only \
   -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
   -p "ExecStopPost=/bin/rm -f -- $W/ALLOW_COLLECTION $W/ALLOW_RUN_SMOKE" \
   /bin/bash "$runner" --lane "$scope" "$lane" >"$F/reports/lane-$lane-systemd.log" 2>&1 &
  pids+=("$!")
 done
 for pid in "${pids[@]}"; do if wait "$pid"; then codes+=(0); else codes+=("$?"); fi; done
 units=()
 for lane in 0 1; do
  for gate in "$F/lane$lane/ALLOW_COLLECTION" "$F/lane$lane/ALLOW_RUN_SMOKE"; do
   if [[ -e "$gate" || -L "$gate" ]]; then closed=false; fi
  done
 done
 printf '{"lane_exit_codes":[%s,%s],"scoped_permits_closed":%s}\n' "${codes[0]}" "${codes[1]}" "$closed" >"$F/reports/supervisor-result.json"
 audit >"$F/reports/preservation-report.json" || true
 as_user "$PY" -m "$(headless_module "$PYTHONPATH" batch160_next)" verify "$B" "$scope"
 as_user env "PYTHONPATH=$F/lane0/repo" "$PY" -u -m "$(headless_module "$F/lane0/repo" batch160_report)" \
  --root "$F" --schedule "$F/schedule.json" | tee "$F/reports/report-generation.log"
 "$PY" - "$F/reports/$scope-report.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    report=json.load(handle)
if not (report['attempted']==report['planned']==report['independently_replayed']
        and report['verification_errors']==0 and not report['errors']):
    raise SystemExit('Replay or preservation gate did not pass')
PY
done
date -u +%FT%TZ >"$B/reports/FINISHED"
