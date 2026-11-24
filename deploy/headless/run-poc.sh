#!/usr/bin/env bash
set -euo pipefail
P="${1:?usage: run-poc.sh /absolute/poc/root}"
P="$(realpath -e "$P")"
test ! -e "$P/ALLOW_COLLECTION"
test ! -e /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION
export PYTHONPATH="$P/contracts${PYTHONPATH:+:$PYTHONPATH}"
"/home/ubuntu/miniforge3/envs/spire/bin/python" -m unittest discover -s "$P/tools" -p 'test_*.py' -v
# Transient unit only: no enable, restart policy, gateway, agent, or action API.
sudo systemd-run --unit=headless-spire-poc-boot --collect --wait \
  -p User=ubuntu -p Group=ubuntu -p WorkingDirectory="$P" \
  -p MemoryMax=768M -p MemorySwapMax=0 -p CPUQuota=100% -p TasksMax=64 \
  -p PrivateNetwork=yes -p PrivateDevices=yes -p PrivateTmp=yes \
  -p ProtectSystem=strict -p ProtectHome=read-only -p ReadWritePaths="$P" \
  -p NoNewPrivileges=yes -p RuntimeMaxSec=600 -p TimeoutStopSec=15 \
  /home/ubuntu/miniforge3/envs/spire/bin/python "$P/tools/probe.py" --root "$P" --duration 300 --interval 5
