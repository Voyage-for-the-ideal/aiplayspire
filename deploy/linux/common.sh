#!/usr/bin/env bash
# Shared defaults. Sourcing this file never launches a service or a game.
set -euo pipefail
AIP_ROOT="${AIP_ROOT:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)}"
config="${AIP_CONFIG:-/etc/aiplayspire/collector.env}"
if [[ -f "$config" ]]; then
    set -a
    source "$config"
    set +a
elif [[ -n "${AIP_CONFIG:-}" ]]; then
    echo "Missing configuration: $config" >&2; exit 78
fi
export AIP_ROOT
export AIP_RUNTIME="${AIP_RUNTIME:-/home/ubuntu/aiplayspire-runtime}"
export AIP_PYTHON="${AIP_PYTHON:-/home/ubuntu/miniforge3/envs/spire/bin/python}"
export AIP_GAME_DIR="${AIP_GAME_DIR:-$AIP_RUNTIME/game}"
export AIP_HOME="${AIP_HOME:-$AIP_RUNTIME/home}"
export AIP_LOG_DIR="${AIP_LOG_DIR:-$AIP_RUNTIME/logs}"
export AIP_WORKER="${AIP_WORKER:-$AIP_ROOT/battle_engine/bin/battle-engine-worker}"
export STS_VALUE_CHECKPOINT="${STS_VALUE_CHECKPOINT:-$AIP_ROOT/selectcard/checkpoints/best_sts_value_model_final.pth}"
export AIP_PROFILE="${AIP_PROFILE:-Collector}"
export AIP_PORT="${AIP_PORT:-5125}"
export AIP_API_PORT="${AIP_API_PORT:-5000}"
export AIP_BUDGET_MS="${AIP_BUDGET_MS:-250}"
export AIP_CHARACTER="${AIP_CHARACTER:-IRONCLAD}"
export AIP_ASCENSION="${AIP_ASCENSION:-10}"
export AIP_INTERVAL="${AIP_INTERVAL:-1.0}"
export AIP_MAX_RUNS="${AIP_MAX_RUNS:-1}"
export OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 NUMEXPR_NUM_THREADS=1
export LP_NUM_THREADS=1 MALLOC_ARENA_MAX=2 CUDA_VISIBLE_DEVICES=""
export MPLBACKEND=Agg MPLCONFIGDIR="$AIP_RUNTIME/cache/matplotlib"
export PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
export PYTHONPATH="$AIP_ROOT${PYTHONPATH:+:$PYTHONPATH}"
export RUN_LOG_DIR="$AIP_LOG_DIR/agent"
export BATTLE_ENGINE_WORKER="$AIP_WORKER"
export BATTLE_STALL_WARN_SECONDS=300

require_gate() {
    if [[ ! -f "$AIP_RUNTIME/$1" ]]; then
        echo "Launch gate absent: $AIP_RUNTIME/$1. No process launched." >&2
        exit 78
    fi
}

claim_lock() {
    exec 9>"$AIP_RUNTIME/$1.lock"
    flock -n 9 || { echo "Instance already running: $1" >&2; exit 75; }
}
