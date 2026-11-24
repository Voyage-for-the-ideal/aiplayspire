#!/usr/bin/env bash
# No permanent service, no HTTP actions. A run requires explicit scoped permits.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
W="$(realpath -e "${1:?isolated runtime root}")"
MODE="${2:---preflight}"
P=/home/ubuntu/headless-spire-poc
case "$W" in "$P"/runtime-acceptance-*) ;; *) echo 'Expected isolated runtime root' >&2; exit 78;; esac
case "$MODE" in --preflight|--run|--inside-unit) ;; *) echo 'Use --preflight or --run' >&2; exit 64;; esac
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
 [[ ! -e "$gate" && ! -L "$gate" ]] || { echo 'Global gate must remain absent' >&2; exit 78; }
done
for gate in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
 [[ -f "$W/$gate" && ! -L "$W/$gate" ]] || { echo "Missing scoped permit: $gate" >&2; exit 78; }
done
entry=$(headless_module "$W/repo" agent)
for file in tools/start-session.sh native-bin/battle-engine-worker value-format12.pth profile-baseline-manifest.json classes/HeadlessSession.class; do
 [[ -f "$W/$file" ]] || { echo "Missing runtime input: $file" >&2; exit 78; }
done
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
[[ -x "$PY" && -x "$W/native-bin/battle-engine-worker" && -x "$W/tools/start-session.sh" ]] || exit 78
"$PY" "$W/tools/check-profile.py" "$W"
export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
if [[ "$MODE" == --preflight ]]; then
 echo "READY isolated=$W roles=4 ascension=10 seeds=5050001..5050004; no run started"
 exit 0
fi
if [[ "$MODE" == --run ]]; then
 systemctl is-active --quiet headless-collection-acceptance.service && { echo 'A collection batch is already active' >&2; exit 78; }
 exec sudo systemd-run --unit=headless-collection-acceptance --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$W/saves" \
  -p MemoryMax=1536M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=96 -p RuntimeMaxSec=900 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes -p ProtectSystem=strict \
  -p ProtectHome=read-only -p ReadWritePaths="$W" -p ReadOnlyPaths="$W/profile-baseline" -p NoNewPrivileges=yes \
  /bin/bash "$W/tools/run-collection-acceptance.sh" "$W" --inside-unit
fi
cd "$W/saves"
exec "$PY" -m "$entry" --root "$W" --launcher "$W/tools/start-session.sh" \
 --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" \
 --ascension 10 --seed 5050001 --max-actions 1500 > "$W/reports/collection-agent.log" 2>&1
