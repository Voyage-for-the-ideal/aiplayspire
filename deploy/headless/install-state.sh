#!/usr/bin/env bash
set -euo pipefail
P="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if systemctl is-active --quiet headless-spire-state.service; then
  echo 'Stop the read-only unit before updating its definition' >&2
  exit 78
fi
systemd-analyze verify "$P/tools/headless-spire-state.service"
sudo install -o root -g root -m 0644 "$P/tools/headless-spire-state.service" \
  /etc/systemd/system/headless-spire-state.service
sudo systemctl daemon-reload
echo 'Installed only; no start or enable operation performed'
