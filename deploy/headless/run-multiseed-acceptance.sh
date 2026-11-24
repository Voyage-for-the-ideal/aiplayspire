#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
W="$(realpath -e "${1:?isolated runtime root}")"
MODE="${2:---preflight}"
RETRY="${3:-}"
P=/home/ubuntu/headless-spire-poc
case "$W" in "$P"/runtime-acceptance-*) ;; *) exit 78;; esac
case "$MODE" in --preflight|--run|--inside-unit) ;; *) exit 64;; esac
[[ -z "$RETRY" || "$RETRY" == --retry-failed ]] || exit 64
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
 [[ ! -e "$gate" && ! -L "$gate" ]] || exit 78
done
for gate in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
 [[ -f "$W/$gate" && ! -L "$W/$gate" ]] || { echo "Missing scoped permit: $gate" >&2; exit 78; }
done
entry=$(headless_module "$W/repo" suite)
for file in tools/start-session.sh native-bin/battle-engine-worker value-format12.pth classes/HeadlessSession.class; do
 [[ -f "$W/$file" ]] || exit 78
done
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
"$PY" "$W/tools/check-profile.py" "$W"
[[ $(df --output=avail -k "$W" | tail -1) -gt 3145728 ]] || { echo 'Expected at least 3 GiB free' >&2; exit 78; }
if [[ "$MODE" == --preflight ]]; then
 echo 'READY single lane; 4 roles x 10 A10 episodes; seeds 5050101..5050140; stop on first difference; no run started'
 exit 0
fi
if [[ "$MODE" == --run ]]; then
 for unit in headless-collection-acceptance headless-multiseed-acceptance; do
  if systemctl is-active --quiet "$unit.service"; then echo 'A collection batch is already active' >&2; exit 78; fi
 done
 exec sudo systemd-run --unit=headless-multiseed-acceptance --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/saves" \
  -p MemoryMax=1536M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=96 -p RuntimeMaxSec=7200 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes -p ProtectSystem=strict \
  -p ProtectHome=read-only -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
  /bin/bash "$W/tools/run-multiseed-acceptance.sh" "$W" --inside-unit "$RETRY"
fi
export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
cd "$W/saves"
ARGS=(); [[ -z "$RETRY" ]] || ARGS+=("$RETRY")
exec "$PY" -u -m "$entry" --root "$W" --launcher "$W/tools/start-session.sh" \
 --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" \
 --ascension 10 --seed-base 5050101 --per-role 10 --max-actions 3000 "${ARGS[@]}" \
 >> "$W/reports/multiseed-agent.log" 2>&1
