#!/usr/bin/env bash
# Explicit dependency installer; does not launch games/services or modify shell rc files.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
[[ "$(uname -sm)" == 'Linux x86_64' ]] || { echo 'Expected Linux x86_64' >&2; exit 78; }
prefix=/home/ubuntu/miniforge3
install_dir="$AIP_RUNTIME/install"
mkdir -p "$install_dir"
if [[ -n "${AIP_INSTALL_PROXY:-}" ]]; then
    export HTTP_PROXY="$AIP_INSTALL_PROXY" HTTPS_PROXY="$AIP_INSTALL_PROXY"
fi
if [[ ! -x "$prefix/bin/conda" ]]; then
    version=26.9.4-2
    installer="Miniforge3-$version-Linux-x86_64.sh"
    curl -fLsS --retry 2 --connect-timeout 20 --max-time 600 --speed-limit 32768 --speed-time 30 \
        -o "$install_dir/$installer" "https://github.com/conda-forge/miniforge/releases/download/$version/$installer"
    echo "bbaf16b3897da24c4be50315934ba74848f617b4495982841d6a0a60e0edbbdf  $install_dir/$installer" | sha256sum -c -
    bash "$install_dir/$installer" -b -p "$prefix"
fi
if [[ ! -x "$AIP_PYTHON" ]]; then
    "$prefix/bin/conda" create --yes --name spire --override-channels -c conda-forge python=3.11 pip
fi
"$AIP_PYTHON" -c 'import sys; assert sys.version_info[:2] == (3, 11)'
"$AIP_PYTHON" -m pip install --no-cache-dir torch==2.7.1 --index-url https://download.pytorch.org/whl/cpu
"$AIP_PYTHON" -m pip install --no-cache-dir --index-url https://pypi.org/simple -r "$AIP_ROOT/deploy/linux/requirements-cpu.txt"
"$AIP_PYTHON" -m pip check
"$AIP_PYTHON" -c 'import torch; assert torch.version.cuda is None; print(torch.__version__, "CPU-only")'
"$AIP_PYTHON" -m pip list --format=json >"$install_dir/cpu-packages.json"
