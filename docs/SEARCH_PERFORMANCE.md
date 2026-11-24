# Java search performance

Whole-run route evaluation was stopped at the user's request. This change only
optimizes Java search bookkeeping; it does not change scores, legal actions,
RNG, search horizons, expansion budgets, or replay guards.

## Bottleneck and implementation

The completed segments already recorded by the route experiment contained
177 search segments / 1,235,390 expansions. Their cumulative metrics attributed
311,018 / 571,704 ms (54.4%) to full-state deduplication-key construction;
240,227 ms was canonicalization/hashing and 70,703 ms JSON encoding. Cumulative
progress messages were counted once per request, not repeatedly.

`SearchStateKey` now caches sorted field-name schemas and quoted plain-ASCII
strings, bulk-copies encoded bytes, and avoids creating a Gson writer for each
number/boolean. Cache hits validate the complete insertion-order field schema;
values are always read again. It never caches mutable JSON trees or snapshot
digests. UUID aliases reset for every state, retaining cross-reference topology.

Caches are thread-local and bounded: 256 schemas, at most 8 insertion-order
variants per field count, and 4,096 strings of at most 256 characters. Unknown
schemas, escaped/Unicode strings and saturated caches retain the original path.
The exact canonical bytes and `murmur3_128` protocol remain unchanged.

## Verification and measured speed

Two existing A20 Champ first-turn snapshots (IRONCLAD and DEFECT) were compared
in isolated Java engines, 15,000 expansions each, one warm-up and three measured
samples. DEEP disables wall-clock-dependent streaming commits **only for this
benchmark**, making the work comparable. The running game's profiles stay intact.

| Snapshot | Old search median | New search median | Request round-trip median |
|---|---:|---:|---:|
| Ironclad | 9.074 s | 6.067 s | 9.186 → 6.184 s |
| Defect | 6.067 s | 4.566 s | 6.135 → 4.648 s |

Canonical hashing alone improved 2.45× / 2.58×. All six measured results per
snapshot matched in commands, final full-state key, and expansion count. The
last sample of each phase/snapshot also passed real-engine replay with normal
state-diff guards (four replay checks). No full game was started by this benchmark.
These two snapshots measure speed, not win-rate improvement.

Reports: `debug/search-speed-20261114/engine-comparison/comparison.json` and its
manifest/samples/replays. The old jar is preserved separately. Version 1.10.1
was built into `_ModTheSpire/mods/` and copied to the installed game's mod folder;
the installed original is backed up under the same experiment's `live-backup/`.

Reproduce from the repository root, using a fresh output directory:

```powershell
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/search-speed-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --output debug/search-speed-new
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=SearchStateKeyTest,SearchStateKeyBenchmarkTest" "-DstateFile=D:/code/aiplayspire/battle_engine/out/champ-a20-replay-20261112/defect-start.json" -DstateWarmup=100 -DstateIterations=200 -DstateSamples=5
```

The package build discovered 115 tests: 109 passed and six optional tests were
skipped. Four require an installed native worker (outside this Java-only change);
the manual key benchmark was subsequently run with `-DstateFile` as above. The
remaining check requires a real manifest with four snapshots per role covering
normal, elite and boss encounters. To enable those optional checks:

```powershell
.\battle_engine\build.ps1
mvn -f scumthespire/pom.xml test "-Dbattleai.nativeWorker=D:/code/aiplayspire/battle_engine/bin/battle-engine-worker.exe" "-Dbattleai.realStateManifest=<four-role-real-snapshot-manifest.json>" "-DstateFile=D:/code/aiplayspire/battle_engine/out/champ-a20-replay-20261112/defect-start.json"
```

Regression coverage includes golden digests, RNG/history/card-instance
distinctions, UUID-reference topology, mutated objects, changing schemas,
cache saturation, numeric spellings, escaping/Unicode and concurrent threads.

## Direct SaveState fingerprints — BattleAiMod 1.10.2

The next implementation removes the whole-snapshot JSON tree from turn-state
deduplication. `SaveStateFields` defines read-only schemas for SaveState, player,
creature, card, pile/container, RNG, hitbox, potion, base relic and map-room data.
Reflection resolves field metadata once; values are read fresh on every key.
The existing canonical byte order, UUID alias/reference topology and Murmur3
128-bit digest are preserved exactly, so wire/replay keys remain compatible.

Serializer overrides, powers/orbs, monsters, selection screens and dynamically
registered StateElements retain their original leaf JSON encoders. They are
streamed into the same key rather than discarded or reduced to base fields.
This preserves opaque extension payloads and unusual serializer conventions;
the optimization removes the large core tree, not every last leaf allocation.
The schema is tied to the current SaveState JSON contract and covered by parity
tests; future changes to that contract need corresponding schema/test updates.
Neither the state format nor replay-diff code was changed.

The controller reports this path as `state_key_direct_ms`; the old standalone
tree-encoding/canonicalization phases report zero. Leaf encoding costs are
included in the direct total, not hidden. `-Dbattleai.verifyDirectStateKey=true`
enables an intentionally slow diagnostic comparing each direct key against
`fromJson(state.jsonEncode())`. It is disabled during timed measurements.

### Comparison against 1.10.1

Same two A20 Champ snapshots, 15,000 expansions, DEEP/no streaming, one warm-up
and three measured samples per case. All numbers below are medians.

| Snapshot | Deduplication key time | Request round-trip |
|---|---:|---:|
| Ironclad | 2.955 → 1.940 s (−34.3%) | 4.702 → 4.682 s (essentially unchanged) |
| Defect | 2.160 → 1.364 s (−36.9%) | 4.639 → 3.137 s (−32.4%) |

Both phases produced identical command sequences, final full-state keys and
15,000 expansions on every measured sample. An additional **untimed** candidate
phase enabled per-state full-reference comparisons on both snapshots, including
warm-ups, and matched the same search results. Six real-engine replay checks
passed across baseline, candidate and verified phases. No whole run was played.

The Ironclad end-to-end median did not show a material gain despite the lower
key cost. The unchanged server status/result loop polls every 1,500 ms, so these
short request timings have coarse latency steps. This change leaves that loop,
search budgets, action ordering and tactical scores intact.

Reports are under `debug/direct-fingerprint-20261114/engine-comparison/`.
The manual baseline jar is 1.10.1, not the earlier 1.10.0 jar. Reproduce with:

```powershell
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/direct-fingerprint-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --verify-candidate --output debug/direct-fingerprint-new
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=DirectStateKeyTest,SearchStateKeyTest,SearchMetricsTest" "-Dbattleai.verifyDirectStateKey=true"
```

The 1.10.2 package build discovered 122 tests: 116 passed, with the same six
optional native/fixture/manual-benchmark skips described above. New coverage
checks all RNG counters/seed/event chances, pile and history order, UUID
reference renaming/topology, mutable state, serializer overrides, dynamic
extension registration, and extensions overriding a root key.
Offline runtime files and JVM temporary extraction directories are on D:.
Version 1.10.2 is deployed to both the repository mod output and the installed
game's BattleAiMod.jar; restart the game to load it. The installed previous jar
is preserved under `debug/direct-fingerprint-20261114/live-backup/`.

## Immediate search completion — BattleAiMod 1.10.3

The server's 1,500-ms sleep is replaced with a request-local `SearchCompletion`
signal backed by `CountDownLatch`. While search is running, its timed wait still
limits progress messages and streamed prefixes to the existing interval.
Completion, failure or cancellation releases that wait immediately; completion
before the wait is retained, with no busy polling or lost notification.
The separate 50-ms controller-startup wait is unchanged.

Every successful terminal path now publishes its result fields and final metrics
before marking the controller done and signalling. The shared controller/start
flag and committed prefix have explicit volatile publication. The response loop
keeps a request-local controller reference. Search scores, expansion budgets,
command ordering, streamed-commit rules, state fingerprints and replay checks
are unchanged. The final response adds `completion_wakeup_ms`, measuring from
terminal publication to the response loop resuming, before final serialization.

### Comparison against 1.10.2

The same two A20 Champ snapshots were run with **15,000 expansions**, DEEP/no
streaming, one warm-up and three measured requests per phase and snapshot.
These are request round-trip medians, including the final result and `DONE`:

| Snapshot | 1.10.2 | 1.10.3 | Reduction |
|---|---:|---:|---:|
| Ironclad | 4.678 s | 4.242 s | 9.3% |
| Defect | 3.146 s | 2.985 s | 5.1% |

All six candidate requests reported a notification wakeup below 1 ms (integer
milliseconds: zero). The shortest observed progress-message gaps were 1,500.3 ms
and 1,501.2 ms respectively: terminal results no longer wait for the next update,
but running-search updates remain rate-limited. Final result serialization and
network transfer still take time; the wakeup measurement is not round-trip time.
This removes waiting latency, rather than accelerating search expansion itself.

Commands, final full-state keys and 15,000-expansion counts were identical on all
12 measured requests. The final sample of each phase/snapshot also passed normal
real-engine replay/diff validation (four checks). Source snapshot SHA-256 hashes
were unchanged. No whole game was started. Reports and exact commands/state keys
are under `debug/result-notify-20261114/engine-comparison/`.

The package build discovered **129 tests: 123 passed, six optional skipped** for
the same native-worker/real-fixture/manual-benchmark prerequisites listed above.
Seven new tests cover retained early completion, result-field publication,
failure, cancellation, progress limiting, interruption and timeout semantics.

Reproduce from the repository root with a fresh output directory:

```powershell
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=SearchCompletionTest,SearchBudgetTest,SearchProfileTest,SearchStateKeyTest,DirectStateKeyTest"
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/result-notify-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --output debug/result-notify-new
```

Version 1.10.3 is deployed to the repository mod output and the installed game's
BattleAiMod.jar. The prior installed jar is backed up under
`debug/result-notify-20261114/live-backup/`. Restart the game/search backend to
load the new jar. Offline runtimes and JVM temporary files remain on D:.

## Canonical schema/primitive fast paths — BattleAiMod 1.10.4

### Remaining bottleneck

The 1.10.3 measurements still attributed about 51% / 55% of reported elapsed
time to full-state fingerprints. A separate **untimed** Java 8 `-Xprof` run on
the same two snapshots identified canonical `writeString`, `writeSnapshot` and
streaming-hasher byte ingestion as hot compiled methods. Game action execution,
`AbstractCard.applyPowers` and player-state restoration also appeared. These flat
samples include startup/JIT activity; they locate methods rather than supplying
precise per-request time percentages. The diagnostic log is preserved under
`debug/search-hotspots-20261114/profiling/profile/server.log`.

### Implementation

- Pre-encode immutable, canonically quoted field names plus `:` in core schemas
  and in the existing bounded JSON-shape cache. Shapes still validate the full
  incoming key order, and dynamic root extensions still invalidate their schema.
- Use typed reflection getters for primitive core fields, avoiding boxing and
  general object dispatch for every numeric/boolean card field.
- Write integral wrapper values directly into a fixed 20-byte thread-local
  scratch buffer, including `Long.MIN_VALUE`. Floating-point/decimal/parsed
  numeric spellings retain their original encoders, including negative zero,
  exponent notation and decimal scale.

These are immutable metadata/encoding optimizations. Snapshot values, JSON trees
and digests are not cached. Full RNG, ordered piles/histories, card-instance UUID
topology, extension/overridden serializer payloads and replay guards remain in
the key. No search scores, actions, pruning, horizons or budgets were changed;
no dependencies were added. Progress remains rate-limited to 1,500 ms and terminal
notifications remain immediate. The benchmark can now accept optional JVM
diagnostics and requests a normal test-engine shutdown to flush profiler output.

A prefix/integer-only prototype lowered key cost but did not improve Ironclad
round-trip time in its initial three-sample test. Its raw results are retained
under `debug/search-hotspots-20261114/engine-comparison/`; it was not deployed.
The final implementation adds typed primitive getters, and the final comparison
below uses the separate `engine-comparison-typed/` directory.

### Final comparison against 1.10.3

Same two A20 Champ first-turn snapshots, **15,000 expansions** per request,
DEEP/no streaming, one warm-up and **five** measured requests per phase/snapshot.
Both timed JVMs have no diagnostic flags. All numbers below are medians:

| Snapshot | Fingerprint time (1.10.3 → 1.10.4) | Request round-trip | Round-trip reduction |
|---|---:|---:|---:|
| Ironclad | 2.082 → 1.490 s (−28.4%) | 3.994 → 3.452 s | 13.6% |
| Defect | 1.499 → 1.125 s (−24.9%) | 2.799 → 2.310 s | 17.5% |

All 20 timed requests match the previous release's commands, final full-state
key and exact 15,000-expansion count; all stop at `EXPANSION_LIMIT`. A separate
untimed candidate phase checks **every** direct fingerprint against the complete
JSON canonical reference on both snapshots, including warm-ups, and reaches the
same commands/final keys. Six normal real-engine replay/diff checks pass across
the three phases. Original source snapshot SHA-256 hashes are unchanged.
No whole game was played.

### What is left after this change

Median component durations in 1.10.4, with approximate shares of the reported
elapsed metric (which also includes final result preparation):

| Component | Ironclad | Defect |
|---|---:|---:|
| Full-state fingerprint | 1,490 ms / 44.8% | 1,125 ms / 50.4% |
| State capture | 277 ms / 8.3% | 234 ms / 10.5% |
| State restore | 273 ms / 8.2% | 191 ms / 8.5% |
| Tactical evaluation | 83 ms / 2.5% | 57 ms / 2.6% |
| Not separately attributed | 1,200 ms / 36.1% | 627 ms / 28.1% |

The raw evaluator counters are process-cumulative, unlike the other counters.
Evaluation durations above are **per-request differences**, starting from that
case's warm-up result, not the misleading cumulative `evaluation_ms` value.
Component medians are independently calculated, so their residual is an
approximation. The residual contains engine action/card-power work, command
enumeration/ordering, miscellaneous search bookkeeping, runtime/GC and final
response preparation; it has not been precisely subdivided. Further work should
profile those boundaries and remaining fingerprint dispatch/leaf encoding/byte
ingestion. Evaluation scoring itself is a small measured share.

### Verification and artifacts

`mvn package` discovered **133 tests: 127 passed, six optional skipped** for the
same native-worker/real-fixture/manual-benchmark prerequisites described above.
Four additional tests cover integral wrappers/limits and buffer boundaries,
escaped/Unicode/oversized field names, signed primitive/seed extremes, and
float spellings/boolean changes. Existing mutation, serializer-override, dynamic
extension, RNG/history, concurrency and cache-saturation tests also pass.

Primary reports are in
`debug/search-hotspots-20261114/engine-comparison-typed/`: `comparison.json`,
`component-summary.json`, `verification.json`, the hashed manifest, per-request
samples/results and all replay reports. Reproduce from the repository root:

```powershell
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=DirectStateKeyTest,SearchStateKeyTest,SearchMetricsTest,SearchCompletionTest" "-Dbattleai.verifyDirectStateKey=true"
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/search-hotspots-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --samples 5 --verify-candidate --output debug/search-hotspots-new
```

The baseline archive is 1.10.3. Version 1.10.4 has been deployed to both the
repository output and installed game, with SHA-256 matching the timed candidate.
The previous installed jar is preserved under
`debug/search-hotspots-20261114/live-backup/`. Restart the game/search backend to
load it. Runtime files and JVM temporary extraction remain on D:.

## Fingerprint stage breakdown — BattleAiMod 1.10.5

### Method and baseline split

Two separate **untimed** 1.10.4 engines ran the existing Ironclad/Defect A20
snapshots, 15,000 expansions per request, one warm-up and five measured requests.
The bundled Java 8 HPROF agent used `cpu=samples,interval=2,depth=96,cutoff=0`.
Only stacks inside `BattleAiController.registerTurnState` →
`SearchStateKey.fromSaveState` were retained, excluding unrelated startup and
other-thread activity. This yielded 3,478 Ironclad and 3,184 Defect samples.

`analyze_fingerprint_profile.py` assigns mutually exclusive buckets, giving
nested hash work priority over encoding, then traversal:

- **Traversal:** field reads, schema selection, type dispatch, collection/JSON
  iteration and remaining fingerprint bookkeeping.
- **Encoding:** canonical scalar/string/UUID formatting, leaf JSON construction,
  structural output and byte-buffer copies.
- **Hash:** Guava Murmur3-128 initialization, byte ingestion and finalization.

The following durations are **sampling estimates**, obtained by multiplying
exclusive CPU-sample shares by the unprofiled 1.10.4 fingerprint median. They are
not three direct stopwatch measurements. Profiling and timing runs are separate;
production has no per-field timers. Safepoint/sampling and run-to-run variation
limit the precision of these estimates.

| Stage | Ironclad (1,463 ms total) | Defect (1,130.5 ms total) |
|---|---:|---:|
| Traversal / reads / dispatch | ≈236 ms / 16.2% | ≈218 ms / 19.3% |
| Encoding / buffering | ≈968 ms / 66.2% | ≈734 ms / 64.9% |
| Murmur3 hashing | ≈258 ms / 17.7% | ≈179 ms / 15.8% |

The largest sampled frames included small `writeBytes` operations, ASCII output
and repeated `UUID.toString`, rather than hashing alone. The normal direct path
remains fused; legacy `state_key_encode_ms` / `state_key_canonical_hash_ms` fields
remain zero because they describe the removed whole-tree pipeline. The separate
profile reports carry this new decomposition.

### Encoding optimization

`writeBytes` now directly copies a fitting chunk into the current 8-KiB block,
avoiding the generic chunk loop/`Math.min` on most field prefixes/cached strings.
ASCII values check available space once instead of checking at every byte.
Cross-block/oversized writes retain the existing path and exact byte order.

Within each fingerprint, canonical UUID objects can now receive aliases directly
without first constructing UUID text. A capped 512-entry per-hash object table
bridges exact lowercase, full-length canonical UUID strings from opaque JSON
leaves in either encounter order. Uppercase, shortened and arbitrary UUID strings
keep their original case-sensitive distinction. Saturation uses the original
string path, and both identity tables reset before every state. Plain `fromJson`
hashing retains string-only alias handling. Full instance/reference topology,
RNG, ordered piles/histories, extensions and replay guards remain intact.
No hash algorithm, score, search order, horizon, budget or dependency was changed.

### Unprofiled, counterbalanced comparison against 1.10.4

The final candidate was tested baseline-first and then candidate-first. Each
phase/case used **three untimed warm-ups and five timed 15,000-expansion requests**
with DEEP/no streaming and no profiler. Thus the combined medians below use ten
requests per version/snapshot, 40 timed requests in total:

| Snapshot | Fingerprint time | Request round-trip |
|---|---:|---:|
| Ironclad | 1.463 → 1.2585 s (−14.0%) | 3.206 → 3.256 s (+1.6%) |
| Defect | 1.1305 → 0.901 s (−20.3%) | 2.341 → 2.020 s (−13.7%) |

Fingerprint medians improved in both execution orders for both snapshots.
Ironclad round-trip varied: +7.9% in the baseline-first round and −5.5% in the
reversed round. Its combined result does **not** demonstrate an end-to-end gain.
The new fingerprint savings should not be represented as equal overall savings;
the variation outside fingerprint construction still needs further attribution.

An initial object-to-text caching prototype, before direct UUID-object aliases,
is retained under `engine-comparison/`; it was not deployed. Final-candidate runs
are `engine-comparison-final/` and `engine-comparison-reversed/`. The benchmark
now accepts `--warmups` (default still one) and `--candidate-first` so warm-up
settings and execution order are explicit and reproducible.

### Correctness, build and artifacts

All 40 timed requests match prior-release commands, full final-state keys and
exactly 15,000 expansions. A separate untimed final-candidate phase verifies every
direct key against the full JSON reference on both snapshots, including warm-ups.
Ten normal replay/diff checks pass across the final and reversed comparisons.
Original source snapshot hashes are unchanged; no whole game was played.

The package build reports **136 tests: 130 passed, six optional skipped** for the
same worker/real-fixture/manual-parameter prerequisites described above. Three
additional Java tests cover byte-buffer edges, opaque UUID strings in both
orders (including uppercase/shortened/non-UUID forms), and >512 identities with
state resets. The new profile analyzer's five tests also pass, covering nesting,
startup exclusion, complete-table selection, sample accounting and invalid data.

Artifacts are under `debug/fingerprint-breakdown-20261114/`:

- `combined-comparison.json`, `verification.json`, `ironclad-breakdown.json` and
  `defect-breakdown.json` contain the final summary and full sampling counts.
- `profiling/manifest.json` and each case's `launch.json` preserve the exact
  baseline jar/snapshot hashes, JVM command and profiler settings; raw HPROF
  reports and profiled sample results are retained on D:.
- Final/reversed comparison folders contain hashed manifests, all per-request
  commands/results and replay checks. Timed runs have no profiler flags.

Reproduce from the repository root, using fresh output directories:

```powershell
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=DirectStateKeyTest,SearchStateKeyTest" "-Dbattleai.verifyDirectStateKey=true"
conda run --no-capture-output -n spire python -m unittest discover -s scumthespire/tools -p test_fingerprint_profile.py
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/fingerprint-breakdown-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --nodes 15000 --warmups 3 --samples 5 --verify-candidate --output debug/fingerprint-new-forward
conda run --no-capture-output -n spire python scumthespire/tools/benchmark_search_speed.py --baseline-jar debug/fingerprint-breakdown-20261114/baseline/BattleAiMod.jar --snapshots battle_engine/out/champ-a20-replay-20261112/ironclad-start.json battle_engine/out/champ-a20-replay-20261112/defect-start.json --nodes 15000 --warmups 3 --samples 5 --candidate-first --output debug/fingerprint-new-reversed
conda run --no-capture-output -n spire python scumthespire/tools/analyze_fingerprint_profile.py debug/fingerprint-breakdown-20261114/ironclad.hprof.txt --state-key-ms 1463 --output debug/ironclad-fingerprint-analysis.json
```

Version 1.10.5 is deployed to the repository output and installed game, with the
installed SHA-256 matching the final timed candidate. The installed 1.10.4 jar is
backed up under `debug/fingerprint-breakdown-20261114/live-backup/`. Restart the
game/search backend to load it; progress limiting and completion notification
remain as before.

## Longer Java BALANCED deadline — BattleAiMod 1.10.6

The default Java BALANCED search deadline increases from **30,000 to 90,000 ms**.
The expansion cap remains **15,000 per segment**. FAST/DEEP budgets, streaming
commits, replanning, immediate completion notification, scoring and fingerprints
retain their existing behavior. A completed/exhausted/victorious search can still
return earlier; 90 seconds is a ceiling, not a fixed wait or a guarantee that
every possible state reaches the node cap.

Both the normal Java client request and the server's missing-timeout default use
`SearchProfile.BALANCED.timeoutMillis()`. The existing client budget validator
accepts 90,000 ms. Its five-second socket read timeout logs and retries instead
of aborting the search, so it does not impose an earlier hard cutoff. Explicit
`timeout_ms` or `-Dbattleai.clientBudgetMs=...` overrides retain precedence.
External/native BALANCED remains independently budgeted at 2,000 ms. Historical
fixed-budget route experiments retain their explicit 30,000-ms configurations;
their stored comparison conditions have not been retroactively changed.

Injected-clock tests verify that BALANCED continues at 30 seconds and expires at
90 seconds, while the inclusive 15,000-expansion limit still applies. Additional
tests cover all Java profile budgets, unchanged native budgets, the client limit
and explicit overrides. The previous native/Java contract assertion was updated
to the new specified Java default. `mvn package` reports **140 tests: 134 passed,
six optional skipped** for the worker/real-fixture/manual-parameter prerequisites
documented above. No live game or snapshot benchmark was started for this budget
configuration change.

```powershell
mvn -f scumthespire/pom.xml package
mvn -f scumthespire/pom.xml test "-Dtest=SearchProfileTest,SearchBudgetTest,NativeBackendContractTest"
```

Build/deployment records and the preserved 1.10.5 jar are under
`debug/balanced-timeout-20261114/`. Version 1.10.6 is deployed to the repository
output and installed game; restart both the game and Java search backend to load
the extended default. Current operating-budget documentation is in `AUTOFIGHT.md`.
