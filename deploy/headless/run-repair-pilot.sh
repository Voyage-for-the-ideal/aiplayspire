#!/usr/bin/env bash
# Run inside a bounded, isolated transient unit. A completed pilot is immutable.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
W=$(realpath -e "${1:?pilot root}"); shift
[[ "$W" =~ ^/home/ubuntu/headless-spire-poc/runtime-acceptance-batch160-repairs-v(2|3(-unfinished)?)/pilot-[a-z0-9-]+$ && $# -gt 0 ]]
[[ ! -e "$W/ALLOW_COLLECTION" && ! -e "$W/ALLOW_RUN_SMOKE" ]]
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do [[ ! -e "$gate" && ! -L "$gate" ]]; done
mkdir "$W/reports"
cd "$W/repo"
export PYTHONPATH="$PWD" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY
trap 'rm -f -- "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"' EXIT
set -o noclobber
printf '%s\n' 'bounded isolated original-seed pilot' > "$W/ALLOW_RUN_SMOKE"
printf '%s\n' 'censored validation traces only' > "$W/ALLOW_COLLECTION"
/home/ubuntu/miniforge3/envs/spire/bin/python -u -m "$(headless_module "$PYTHONPATH" repair_pilot)" \
  --root "$W" --batch "$P/runtime-acceptance-batch160-v1" \
  --previous "$P/runtime-acceptance-batch160-repairs-v1/reports/repair-audit.json" --seeds "$@" > "$W/reports/pilot.log" 2>&1
