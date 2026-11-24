#!/usr/bin/env bash
set -euo pipefail
umask 027
P="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
  if [[ -e "$gate" || -L "$gate" ]]; then
    echo 'Collection gate must stay absent' >&2
    exit 78
  fi
done
test -f "$P/probe-classes/ReadonlyStateServer.class"
grep -qx pre-run-state-v1 "$P/reports/readonly-build.ok"
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
SAVE="$(mktemp -d "$P/saves/http-XXXXXXXX")"
echo "READONLY_STATE_PROFILE $SAVE"
cd "$SAVE"
exec "$P/toolchains/jdk21/bin/java" -Xms32m -Xmx512m -XX:+UseSerialGC \
  -XX:ActiveProcessorCount=1 -Djava.awt.headless=true \
  -cp "$P/probe-classes:$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar" \
  ReadonlyStateServer "$P/upstream/data" "$SAVE" 5000
