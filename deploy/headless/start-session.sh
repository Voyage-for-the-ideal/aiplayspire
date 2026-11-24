#!/usr/bin/env bash
set -euo pipefail
W="$(realpath -e "$(dirname "$0")/..")"
P=/home/ubuntu/headless-spire-poc
case "$W" in "$P"/runtime-acceptance-*) ;; *) echo 'Expected isolated runtime root' >&2; exit 78;; esac
[[ -f "$W/ALLOW_RUN_SMOKE" && ! -L "$W/ALLOW_RUN_SMOKE" ]] || { echo 'Run permit missing' >&2; exit 78; }
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
[[ -d "$W/profile-baseline" && -f "$W/profile-baseline-manifest.json" ]] || { echo 'Frozen game profile missing' >&2; exit 78; }
/home/ubuntu/miniforge3/envs/spire/bin/python "$W/tools/check-profile.py" "$W" >&2
mkdir -p "$W/sessions"
SAVE="$(mktemp -d "$W/sessions/attempt-XXXXXXXX")"
cp -a "$W/profile-baseline/." "$SAVE/"
cd "$SAVE"
exec "$P/toolchains/jdk21/bin/java" -Xms32m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.awt.headless=true \
 -cp "$W/classes:$P/probe-classes:$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar" \
 HeadlessSession "$P/upstream/data" "$SAVE" "$W"
