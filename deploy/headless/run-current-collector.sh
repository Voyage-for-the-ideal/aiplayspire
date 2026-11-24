#!/usr/bin/env bash
set -euo pipefail
P=/home/ubuntu/headless-spire-poc
W="$(realpath -e "$P/current-collector")"
case "$W" in "$P"/runtime-acceptance-*) ;; *) echo 'Unexpected collector root' >&2; exit 78;; esac
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
[[ -f "$W/combat-search.json" && -x "$W/native-bin/battle-engine-worker" ]] || exit 78
"$PY" "$W/repo/deploy/headless/release_worker.py" verify "$W/native-bin/battle-engine-worker" || exit 78
"$PY" "$W/tools/check-profile.py" "$W"
export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
if [[ "${1:-}" == --preflight ]]; then
    cat "$W/combat-search.json"
    exit 0
fi
for gate in ALLOW_RUN_SMOKE ALLOW_COLLECTION; do
 [[ -f "$W/$gate" && ! -L "$W/$gate" ]] || { echo "Missing scoped permit: $gate" >&2; exit 78; }
done
cd "$W/saves"
exec "$PY" -m sts_ai_framework.headless.agent --root "$W" --launcher "$W/tools/start-session.sh" \
  --native "$W/native-bin/battle-engine-worker" --checkpoint "$W/value-format12.pth" --ascension 5 "$@"
