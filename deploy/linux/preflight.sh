#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
cd "$AIP_ROOT"
exec "$AIP_PYTHON" "$AIP_ROOT/deploy/linux/preflight.py" "$@"
