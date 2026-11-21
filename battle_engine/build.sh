#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
jobs="${1:-1}"
[[ "$jobs" =~ ^[1-4]$ ]] || { echo 'Usage: build.sh [jobs: 1..4]' >&2; exit 2; }
cmake -S "$root" -B "$root/build/linux" -DCMAKE_BUILD_TYPE=Release
cmake --build "$root/build/linux" --parallel "$jobs"
