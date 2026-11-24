#!/usr/bin/env bash
set -euo pipefail
P="$(realpath -e "${1:?PoC root}")"
W="$(realpath -e "${2:?isolated flow workspace}")"
case "$W" in "$P"/offline-flow-contract-*) ;; *) echo 'Expected isolated flow workspace' >&2; exit 78;; esac
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
  if [[ -e "$gate" || -L "$gate" ]]; then echo 'Collection gate must stay absent' >&2; exit 78; fi
done
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1
export JAVA_HOME="$P/toolchains/jdk21"
CP="$P/probe-classes:$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar"
mkdir -p "$W/classes" "$W/reports" "$W/saves"
/usr/bin/time -v "$JAVA_HOME/bin/javac" -J-Xmx256m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 \
  -encoding UTF-8 -proc:none -cp "$CP" -d "$W/classes" \
  "$W/inputs/EventStateExtractor.java" "$W/tools/semantics/ChoiceScreenUtils.java" \
  "$W/tools/HeadlessStateExport.java" "$W/tools/StateExportFixtureTest.java" \
  "$W/tools/HeadlessMatchGame.java" "$W/tools/HeadlessInteractionExport.java" "$W/tools/InteractionExportFixtureTest.java" \
  "$W/tools/HeadlessFlowExport.java" "$W/tools/HeadlessFlowKernel.java" "$W/tools/FlowExportFixtureTest.java" \
  >"$W/reports/compile.log" 2>"$W/reports/compile-resources.txt"
SAVE="$(mktemp -d "$W/saves/fixture-XXXXXXXX")"
(cd "$SAVE"
 "$JAVA_HOME/bin/java" -Xms32m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.awt.headless=true \
   -cp "$W/classes:$CP" FlowExportFixtureTest "$P/upstream/data" "$SAVE" "$W/reports/flow-fixtures.json") \
   >"$W/reports/java-fixture-test.log" 2>&1
cat "$W/reports/java-fixture-test.log"
/home/ubuntu/miniforge3/envs/spire/bin/python -m unittest \
  sts_ai_framework.tests.test_headless_state sts_ai_framework.tests.test_headless_interaction sts_ai_framework.tests.test_headless_flow -v \
  >"$W/reports/python-contract-tests.log" 2>&1
tail -5 "$W/reports/python-contract-tests.log"
for kind in interaction flow; do
 /home/ubuntu/miniforge3/envs/spire/bin/python "$W/tools/check_${kind}_fixture.py" \
   --fixture "$W/reports/${kind}-fixtures.json" --output "$W/reports/java-to-${kind}.json"
done
