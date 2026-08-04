# Headless Battle Integration Tests

`battle_test` is an explicit local integration suite for BattleAiMod. It does not run as part of `mvn test`, adds no Python dependencies, and does not build or overwrite any mod JAR.

## Prepare

Build the existing mods in dependency order before running the suite:

```powershell
cd STSStateSaver; mvn package
cd ..\LudicrousSpeed; mvn package
cd ..\scumthespire; mvn package
```

The runner loads only BaseMod, StSLib, SaveStateMod, LudicrousSpeed, and BattleAiMod. It deliberately excludes FightPredictor. Supply the game directory with `--game-dir`, `STS_GAME_DIR`, or use the documented Windows default.

## Run

```powershell
python -m unittest discover battle_test/tests
python battle_test/run.py --game-dir "D:\Program Files\Slay the Spire"
python battle_test/run.py --game-dir "D:\Program Files\Slay the Spire" --isolation-check
python battle_test/run.py --fixture watcher_time_eater --keep-artifacts
```

Each run uses Java `DataInputStream` / `DataOutputStream.writeUTF` compatible frames and ASCII JSON. Output is stored in `battle_test/artifacts/<timestamp>/` when the run fails or `--keep-artifacts` is set. The runner records request/response JSON, server stdout/stderr, and a summary; it terminates the whole process tree after the test-mode shutdown request or a timeout.

## Fixtures

`battle_test/fixtures/manifest.json` defines exactly twelve captured `SaveState` files, three per character. Capture each JSON state from a real game, place it beside the manifest with the declared name, and keep the capture complete. Do not replace a missing capture with a synthetic or partial state: a missing fixture is an explicit failed result.

For every fixture, the runner checks server readiness, a completed protocol response, valid metrics, and a canonical final state key. The BattleAiMod test protocol additionally replays the returned commands, checks every state diff, and compares its replay state key to the search state key.

`known_failure` entries must include a concrete cause and captured logs. A formerly known failure that passes is reported as a failure so stale exemptions are removed.

## Isolation

The normal suite runs all fixtures in one server process. `--isolation-check` runs the whole suite three times and then repeats a representative fixture per character in a fresh process. It compares final state keys, stop reasons, completion state, and replan state while ignoring timing metrics. Any divergence retains mixed isolation and records the predecessor fixture and first divergent case in the artifact summary.

Failure categories are: startup/install validation, protocol framing, server crash/timeout, invalid search metrics, command decode/replay failure, diff divergence, final-state-key divergence, and isolation divergence.
