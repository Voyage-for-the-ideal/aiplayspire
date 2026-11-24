#!/usr/bin/env bash
# The only writes are to a new, resource-scoped pilot directory.
set -euo pipefail
source "$(dirname "$(realpath -e "$0")")/python-entry.sh"
P=/home/ubuntu/headless-spire-poc
W=$(realpath -e "${1:?pilot root}"); shift
[[ "$W" =~ ^/home/ubuntu/headless-spire-poc/runtime-acceptance-batch160-repairs-v3(-unfinished)?/pilot-new-[01]$ && $# -gt 0 ]]
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"; do
 [[ ! -e "$gate" && ! -L "$gate" ]]
done
mkdir "$W/reports"
cd "$W/repo"
export PYTHONPATH="$PWD" PYTHONDONTWRITEBYTECODE=1 OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
trap 'rm -f -- "$W/ALLOW_COLLECTION" "$W/ALLOW_RUN_SMOKE"' EXIT
set -o noclobber
printf '%s\n' 'isolated original-seed release verification' > "$W/ALLOW_RUN_SMOKE"
printf '%s\n' 'verification traces only; no training' > "$W/ALLOW_COLLECTION"
exec </dev/null
/home/ubuntu/miniforge3/envs/spire/bin/python -u -m "$(headless_module "$PYTHONPATH" release2)" pilot \
  --root "$W" --batch "$P/runtime-acceptance-batch160-v2" --source "$(dirname "$W")/evidence/release1-outcomes.json" \
  --seeds "$@" > "$W/reports/pilot.log" 2>&1
