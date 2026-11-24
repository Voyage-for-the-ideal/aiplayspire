#!/usr/bin/env bash
set -euo pipefail
P="${1:?usage: build-poc.sh /absolute/poc/root}"
P="$(realpath -e "$P")"
export JAVA_HOME="$P/toolchains/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS='-Xms32m -Xmx768m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.awt.headless=true'
test -f "$P/upstream/build-headless.sh"
test -f "$P/reference/desktop-1.0.jar"
rm -f "$P/reports/readonly-build.ok"
unset DISPLAY WAYLAND_DISPLAY
/home/ubuntu/miniforge3/envs/spire/bin/python "$P/tools/patch_upstream.py" "$P"
cd "$P/upstream"
/usr/bin/time -v bash ./build-headless.sh >"$P/reports/build.log" 2>"$P/reports/build-resource.txt"
mkdir -p "$P/probe-classes" "$P/saves"
javac -encoding UTF-8 -proc:none -cp "$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar" \
  -d "$P/probe-classes" "$P/tools/PocReadProbe.java" "$P/tools/RngProbe.java" \
  "$P/tools/NoRunGuard.java" "$P/tools/ReadonlyStateServer.java" "$P/tools/BootStateTest.java"
SAVE="$(mktemp -d "$P/saves/boot-test-XXXXXXXX")"
java -cp "$P/probe-classes:$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar" \
  BootStateTest "$P/upstream/data" "$SAVE" >"$P/reports/boot-state-test.log" 2>&1
cat "$P/reports/boot-state-test.log"
java -version
printf '%s\n' pre-run-state-v1 > "$P/reports/readonly-build.ok"
