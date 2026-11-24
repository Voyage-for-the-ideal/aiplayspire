#!/usr/bin/env bash
# Synthetic/offline checks only. No game, collector, training job or service start.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
cd "$AIP_ROOT"
session="$("$AIP_PYTHON" -c 'import uuid; print(uuid.uuid4().hex)')"
report="$AIP_RUNTIME/reports/offline-$session"
mkdir -p "$report"
echo "REPORT_DIR=$report"
"$AIP_PYTHON" "$AIP_ROOT/deploy/linux/preflight.py" --mode offline --model >"$report/preflight.json"
status=0
run_check() {
    local name="$1"; shift
    if /usr/bin/time -v -o "$report/$name.resources.txt" "$@" >"$report/$name.log" 2>&1; then
        echo "$name PASS" | tee -a "$report/summary.txt"
    else
        echo "$name FAIL (see $report/$name.log)" | tee -a "$report/summary.txt"
        tail -n 18 "$report/$name.log"
        status=1
    fi
}
run_check deployment "$AIP_PYTHON" -m unittest discover -s deploy/linux -p 'test_*.py' -v
run_check backend "$AIP_PYTHON" -m unittest discover -s battle_engine/tests -t . -v
run_check framework "$AIP_PYTHON" -m unittest discover -s sts_ai_framework/tests -t . -v
pushd "$AIP_ROOT/selectcard/src" >/dev/null
run_check value "$AIP_PYTHON" -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api test_live_learning test_metric_precision -v
popd >/dev/null
run_check probe "$AIP_PYTHON" "$AIP_ROOT/deploy/linux/offline-probe.py" --output "$report/probe.json"
# Prove that the validation did not leave a live game/collector endpoint.
"$AIP_PYTHON" "$AIP_ROOT/deploy/linux/preflight.py" --mode offline >"$report/postflight.json"
if [[ -r /proc/self/cgroup ]]; then
    cgroup="$(awk -F: '$1 == "0" {print $3}' /proc/self/cgroup)"
    printf '%s\n' "$cgroup" >"$report/cgroup-path.txt"
    for name in memory.current memory.peak memory.max memory.events memory.pressure; do
        cat "/sys/fs/cgroup$cgroup/$name" >"$report/cgroup-$name.txt"
    done
fi
echo "Offline validation finished with status=$status; report=$report"
exit "$status"
