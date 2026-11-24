#!/usr/bin/env bash
set -euo pipefail
P="$(realpath -e "${1:?PoC root}")"
W="$(realpath -e "${2:?isolated combat workspace}")"
case "$W" in "$P"/offline-combat-contract-*) ;; *) echo 'Expected isolated combat workspace' >&2; exit 78;; esac
for gate in "$P/ALLOW_COLLECTION" /home/ubuntu/aiplayspire-runtime/ALLOW_COLLECTION; do
 if [[ -e "$gate" || -L "$gate" ]]; then echo 'Collection gate must stay absent' >&2; exit 78; fi
done
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS
export PYTHONPATH="$W/repo" PYTHONDONTWRITEBYTECODE=1
export BATTLE_ENGINE_WORKER="$W/native-bin/battle-engine-worker"
export JAVA_HOME="$P/toolchains/jdk21"
CP="$P/probe-classes:$P/upstream/out:$P/upstream/lib/gson-2.8.8.jar"
mkdir -p "$W/classes" "$W/reports" "$W/saves"
"$JAVA_HOME/bin/javac" -J-Xmx256m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 \
 -encoding UTF-8 -proc:none -cp "$CP" -d "$W/classes" \
 "$W/inputs/EventStateExtractor.java" "$W/inputs/BattleCardPools.java" "$W/tools/semantics/ChoiceScreenUtils.java" \
 "$W/tools/HeadlessStateExport.java" "$W/tools/StateExportFixtureTest.java" \
 "$W/tools/HeadlessMatchGame.java" "$W/tools/HeadlessInteractionExport.java" "$W/tools/InteractionExportFixtureTest.java" \
 "$W/tools/HeadlessFlowExport.java" "$W/tools/HeadlessFlowKernel.java" "$W/tools/FlowExportFixtureTest.java" \
 "$W/tools/HeadlessCombatExport.java" "$W/tools/CombatExportFixtureTest.java" > "$W/reports/java-compile.log" 2>&1
for kind in Flow Combat; do
 save=$(mktemp -d "$W/saves/${kind}-XXXXXXXX")
 lower=$(printf '%s' "$kind" | tr '[:upper:]' '[:lower:]')
 (cd "$save"; "$JAVA_HOME/bin/java" -Xms32m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.awt.headless=true \
  -cp "$W/classes:$CP" "${kind}ExportFixtureTest" "$P/upstream/data" "$save" "$W/reports/${lower}-fixtures.json") > "$W/reports/java-${lower}-fixture.log" 2>&1
 cat "$W/reports/java-${lower}-fixture.log"
done
PY=/home/ubuntu/miniforge3/envs/spire/bin/python
cd "$W/repo"
"$PY" -m unittest sts_ai_framework.tests.test_headless_state sts_ai_framework.tests.test_headless_interaction \
 sts_ai_framework.tests.test_headless_flow sts_ai_framework.tests.test_headless_combat -v > "$W/reports/framework-contract-tests.log" 2>&1
"$PY" -m unittest discover -s battle_engine/tests -t . -v > "$W/reports/native-backend-tests.log" 2>&1
for kind in interaction flow; do
 "$PY" "$W/tools/check_${kind}_fixture.py" --fixture "$W/reports/${kind}-fixtures.json" --output "$W/reports/java-to-${kind}.json"
done
"$PY" "$W/tools/check_combat_fixture.py" --fixture "$W/reports/combat-fixtures.json" --worker "$BATTLE_ENGINE_WORKER" --output "$W/reports/java-to-native.json"
tail -5 "$W/reports/framework-contract-tests.log"
tail -5 "$W/reports/native-backend-tests.log"
