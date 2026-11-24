# Resource-aware routing v2

Opt in without changing the default six-room baseline:

```powershell
conda run --no-capture-output -n spire python -m sts_ai_framework --route-policy resource_v2
```

`planner` remains the current six-room static-context baseline. `legacy` is the
older one-room scorer, not the A/B baseline. Other screens, potions and the value
checkpoint are unchanged. No model is trained and no dependency is added.

## Forecast semantics

`routing/strength.py` builds an energy-adjusted visible-deck profile: frontload,
defense, AoE, scaling and cycling. Numeric card fields are optional (`None` means
unknown); card-ID rules cover multi-hit, poison, orbs and conditional engines.
Rarity is not a strength feature. Coverage and unknown IDs are logged.
CommunicationMod 1.6.4 publishes base damage/block/magic even before combat
initializes temporary card values. The benchmark checks this at the actual
floor-0 start and stops on a missing-metadata preflight rather than running an
uninformative comparison.

`routing/resource.py` contains frozen `ResourcePolicy` hand-set HP/gold priors.
They are estimates, not simulated fights or calibrated win probabilities. Each
path has independent mean/prudent HP, gold, key acquisition probability,
upgrade opportunities and flying-boot charges. Combat recovery is applied only
after surviving the estimated damage. Death of a prefix stays unsafe after a
later rest. Camps and linked treasure rewards are mutually exclusive choices;
shops spend a capped expected budget. Unknown rewards do not invent cards or
relics. Question-mark transitions average public outcome probabilities, with an
additional prudent HP uncertainty margin. Future question probabilities are
held at their currently observed values; hidden event outcomes are not inferred.

Only the current visible Boss affects terminal reserve, discounted by remaining
map distance. No seed resolver, future boss, encounter, or reward is read by the
router. Predicted upgrades use an explicitly approximate strength increment,
not fabricated upgraded card stats. Camp/shop projections do not control those
screens: their original decision modules still execute, and telemetry records
the difference between projection and actual behavior.

`route_candidate` diagnostics include profile, version, visible Boss and the
complete six-room trajectory. `route_prediction_error` compares only a submitted
first step against the next same-act, next-floor map observation; terminal,
run-change and missing-observation cases are labeled separately. Plans are not
injected into value-network inputs.

## Fixed-seed exploratory evaluation

```powershell
conda run --no-capture-output -n spire python -m sts_ai_framework.route_benchmark --output debug/route-resource-new
# Recompute the report without starting games:
conda run --no-capture-output -n spire python -m sts_ai_framework.route_benchmark --output debug/route-resource-new --compare-only
```

The benchmark runs IRONCLAD `RT20IC01`–`03` and DEFECT `RT20DF01`–`03`, A20,
baseline/candidate once each to terminal. Order alternates AB/BA. Each run uses
fresh isolated client/server processes, copied account preferences and mod
configuration. The existing Java `battleai.runSeed` fixture sets the seed.
The existing client-control socket starts the ordinary Java search at readiness
boundaries; the transparent auditing proxy preserves streamed command frames.
Requests must be BALANCED, 15,000 expansions and 30,000 ms. These are per-search
segment budgets, not per-whole-fight budgets. Neither the C++ engine nor native
budget overrides are used.

The manifest freezes source/runtime/checkpoint/account hashes before any run.
Identical LLM requests share a benchmark-only response cache. Start fingerprints
and observed seed-group hashes are checked; seed values are metadata, never
router inputs. Search request/response metrics are saved in `search.jsonl`.
Results include Act II/III/Heart outcomes, terminal floor, Boss-entry resources,
first divergence, and prediction errors. Interruptions/stalls are incomplete
observations, not defeats. Six pairs are exploratory: `promotable` stays false,
and the default policy stays `planner`.

Use a fresh output directory after changing code or runtime. Completed/error
results are preserved, not silently rerun or replaced. A process-level timeout
of three hours marks the run incomplete rather than imposing a game defeat.

## Regression

```powershell
conda run --no-capture-output -n spire python -m unittest discover -s sts_ai_framework/tests -t .
Push-Location selectcard/src
conda run --no-capture-output -n spire python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api
Pop-Location
```
