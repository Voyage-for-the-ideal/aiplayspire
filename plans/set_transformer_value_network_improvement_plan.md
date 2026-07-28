# Set Transformer Value Network Improvement Plan

## Scope

This plan covers the non-combat value network and the decision layer built on top of it:

- `selectcard`
- non-combat routing in `sts_ai_framework`
- event-state extraction in `StSCommunicationMod`

Combat search and combat-state restoration remain governed by
[`battle_ai_remaining_risks.md`](battle_ai_remaining_risks.md). The two plans should remain
separate because their state contracts, training requirements, and failure modes are different.

The event-by-event inventory in [`event_catalog.md`](event_catalog.md) remains the detailed source
of known event cases. This document defines the common representation and implementation order;
it should not duplicate the full event catalog.

## Confirmed Design Constraints

1. Preserve useful prefixes of abandoned runs. A run abandoned in Act 2 can still provide a valid
   Act 1 survival target. Do not replace the existing Act-boundary target with a final-win-only
   target that invalidates all such samples.
2. Do not add an explicit character feature without evidence that it adds information beyond the
   starting deck, starting relic, and HP state already represented by the model.
3. Do not require potion slots, potion state, or relic counters until their `.run` recording and
   game-source semantics have been audited and a reliable reconstruction contract exists.
4. Keep cards and relics in one set encoder. Their interactions are central, and both can share
   learned semantic categories. Add type information only if an ablation shows that it helps.
5. Deck size and curse count remain implicit in item identities and count embeddings unless an
   ablation demonstrates that explicit summary features improve generalization.
6. Global state must condition set encoding earlier than the final value-head concatenation.
7. Changes to normalization, pooling, attention layout, or objectives must be evaluated separately
   before they are combined.

## Current Objective and Abandoned Runs

Keep the current primary target:

```text
Act 1 snapshot -> did the run reach Act 2?
Act 2 snapshot -> did the run reach Act 3?
Act 3 snapshot -> did the run reach the configured late-run boundary?
```

Classify target validity separately from target value:

| Run outcome | Earlier completed Acts | Act containing abandonment | Later Acts |
|---|---|---|---|
| Death with reliable `killed_by` | valid | valid negative | unavailable |
| Victory | valid positive | valid positive | valid where defined |
| Manual abandonment | valid | censored/unknown | unavailable |
| Corrupt or irreconcilable run | unavailable | unavailable | unavailable |

This prevents a manual exit from being treated as a death while retaining completed-Act evidence.
Implementation should carry an explicit `target_valid` or censoring field instead of deciding
validity indirectly from a single run-level filter.

Possible auxiliary targets may be added later, but they must tolerate censoring. Suitable options
include per-Act survival heads with masked losses or a discrete-time survival objective. A final-win
head may use only runs with a known terminal outcome; it must not replace the primary Act target.

## Data and Evaluation Foundation (P0)

### Run-level splits

Assign complete runs, not individual snapshots, to train/validation/test partitions. Multiple
snapshots from one run must never cross partitions. Prefer a time-based held-out test set in addition
to a grouped random validation split.

Persist a stable `run_id`, split assignment, and preprocessing version in processed data. Report
metrics by Act and by snapshot floor so that Act-boundary behavior is visible.

### Canonical item encoding

Use one normalization function in reconstruction, training, and inference:

```text
Bash+          -> (Bash, 1)
Bash+1         -> (Bash, 1)
SearingBlow+3 -> (SearingBlow, 3)
```

Build and freeze the vocabulary before model initialization. Validation and inference must map
unknown items to a dedicated `UNK` token and must not extend the vocabulary.

### Baseline metrics

Record more than accuracy and PR-AUC:

- BCE/log loss and PR-AUC for discrimination
- ROC-AUC as secondary context
- Brier score and expected calibration error (ECE)
- reliability plots per Act
- pairwise choice accuracy and regret once ranking data exists

## Probability and Class Imbalance (P0)

`BCEWithLogitsLoss` combines a numerically stable sigmoid with binary cross-entropy. With no class
weight, minimizing BCE estimates the observed class posterior when the data and model assumptions
hold. A positive `pos_weight` intentionally changes the relative cost of positive errors; the raw
sigmoid output then becomes a cost-sensitive score and is not automatically a calibrated survival
probability.

Evaluate three alternatives on the same run-level split:

1. Unweighted BCE with balanced batches only for optimization stability.
2. Weighted BCE for ranking/discrimination, followed by calibration on an untouched calibration set.
3. Focal loss only if hard-example behavior justifies the extra complexity.

For calibration, compare temperature scaling, Platt scaling, and isotonic regression. Calibration
must be fitted separately from training and evaluated on the test set. Decision ranking should also
be measured before and after calibration; a monotonic calibrator should not change ordering.

## Encoder Architecture Experiments (P1)

### Global conditioning

Replace late-only global fusion with an experiment matrix:

| Variant | Mechanism | Purpose |
|---|---|---|
| A | Current late concat | baseline |
| B | One learned projection of all global features as a Global token | simplest early conditioning |
| C | Several semantic Global tokens | test whether separate HP/economy/progress queries help |
| D | FiLM modulation in each block | condition item features without treating globals as set items |

Start with Variant B. The initial global vector should retain the reliable fields only: floor, HP,
gold, and ascension, with normalization parameters saved in the checkpoint rather than hard-coded in
inference.

Do not assume that more Global tokens are better. Multiple tokens add capacity and can create
redundant summaries. Compare one combined token against semantically separated tokens such as:

```text
[VITALS]   <- HP and max-HP-derived fields when reliable
[ECONOMY]  <- gold
[PROGRESS] <- floor and ascension
```

### Pooling and decision representation

Keep `[CLS]` as the first baseline, then compare:

1. final `[CLS]` only;
2. `[CLS]` plus masked mean pooling;
3. multiple learned pooling queries followed by a small fusion layer.

When Global tokens enter attention, specify whether they are only context or also pooling outputs.
The preferred simple design is: Global token participates in every block, but the value head reads
final `[CLS]` first. Add other pooled outputs only if the ablation improves held-out choice metrics.

### LayerNorm position

Compare the current Post-LN block with a Pre-LN block:

```text
Post-LN: x = LN(x + Attention(x)); x = LN(x + FFN(x))
Pre-LN:  x = x + Attention(LN(x)); x = x + FFN(LN(x))
```

Pre-LN usually gives more stable gradients and makes deeper stacks easier to train. At the current
three-block depth, it is not automatically superior, so compare convergence, gradient norms,
validation loss, calibration, and ranking quality. If Pre-LN is selected, apply a final LayerNorm
before pooling/value heads.

Keep three blocks and four heads fixed during these experiments. Depth/head-count tuning should only
happen after the state contract and objective are stable.

## Pairwise Ranking Objective (P1)

The deployed decision rule chooses the candidate with the largest predicted value, so training should
measure relative ordering as well as absolute survival classification.

For two successor states with evidence that `A` is better than `B`, use a pairwise loss such as:

```text
L_rank = softplus(-(V(A) - V(B)))
```

This loss does not eliminate all small numerical differences. It reduces harmful noise by training
the exact quantity used at decision time: the value difference. Classification BCE can assign nearly
identical scores to two states because both share the same Act-survival label; ranking loss supplies a
gradient that explicitly pushes the better state above the worse one.

Example:

```text
BCE labels:        A=1, B=1        -> both can be scored 0.80 with little penalty
Ranking evidence:  A better than B -> penalize unless V(A) > V(B)
```

Use ranking pairs only when the preference source is defensible:

- controlled simulator/search outcomes;
- matched states with reliable downstream outcomes;
- high-quality expert choices, treated as noisy preferences;
- event outcomes whose effects are deterministic and fully represented.

Do not automatically label every historical `picked` option as better than every `not_picked` option.
Player choices are behavior data, not ground truth. Consider confidence-weighted ranking and a margin:

```text
L_rank = weight * max(0, margin - (V(A) - V(B)))
```

Evaluate pairwise accuracy, top-1 choice accuracy, and decision regret. Also introduce a decision
tolerance: if candidate scores are within an empirically calibrated uncertainty band, use a stable
tie-break rule instead of treating the smallest floating-point difference as meaningful.

## Event State and Effect Contract (P0-P2)

Free-text parsing should not be the long-term source of truth. Define a structured event contract
between the Java game-state extractor and the Python decision engine.

### Event identity

Every event state should expose:

```text
event_id
phase/state identifier
option index
enabled/disabled
continuation type
```

The phase identifier is essential because the same event and option index can mean different things
across pages.

### Typed effect schema

Represent each option as a list or tree of typed effects:

```text
gain_hp / lose_hp / gain_max_hp / lose_max_hp
gain_gold / lose_gold
add_card / remove_card / upgrade_card / transform_card / duplicate_card
obtain_relic / lose_relic
obtain_potion / lose_potion
start_combat
random_outcome
open_grid / open_card_reward / continue_event / leave_event
```

Each effect should carry its amount, target-selection rule, timing, prerequisites, and source. Dynamic
values should be extracted from the event instance or calculated using the same game rules, not parsed
from localized button text.

### Deterministic, stochastic, and sequential outcomes

Use an outcome tree rather than pretending every option is a flat deterministic delta:

```text
Option
  -> immediate effects
  -> probabilistic branches with probabilities
  -> follow-up state or selection screen
  -> optional combat transition
```

Decision evaluation then becomes:

```text
deterministic: V(T(S, option))
stochastic:    sum(probability_i * V(S_i))
sequential:    evaluate the reachable follow-up choices
combat:        combine battle-risk estimate with post-combat rewards
```

If a probability, hidden RNG dependency, potion effect, relic counter, or combat consequence cannot be
reconstructed reliably, mark the option `unsupported` or attach lower confidence. Do not silently
substitute a deterministic approximation.

### Event implementation phases

1. Audit event classes in decompiled game source and map `event_id + phase + option` to effects.
2. Implement deterministic HP, max HP, gold, card, and relic effects first.
3. Implement GRID/card-reward continuations as explicit multi-step decisions.
4. Add stochastic outcome trees where probabilities are source-verifiable.
5. Integrate combat-starting events only after a battle-risk value interface exists.
6. Audit potion and relic-counter recording separately before adding them to the model contract.

Use [`event_catalog.md`](event_catalog.md) as the initial audit queue. Update catalog entries with a
schema-support status rather than relying only on replacement button text.

## Why Variable-Length Sets Work

Self-attention operates on an `n x d` input and produces an `n x d` output; its learned projection
matrices depend on `d`, not on sequence length `n`. The attention matrix is constructed dynamically as
`n x n`, so the same weights work for different numbers of cards and relics.

Batching still requires rectangular tensors. Shorter examples are padded to the longest configured
length and a key-padding mask prevents PAD positions from contributing as keys/values. Pooling through
`[CLS]` or a masked pooling operation converts any valid length back to a fixed-size state vector.

No positional encoding is used, so reordering real item tokens reorders intermediate item outputs but
does not change the pooled set representation, apart from floating-point effects. This makes the
encoder suitable for unordered, variable-size collections. Attention cost remains quadratic in the
number of distinct item tokens; the current maximum of 64 makes full attention reasonable.

## Recommended Execution Order

1. Add run IDs, censoring/target-validity fields, grouped splits, frozen vocabulary, and shared item
   normalization.
2. Establish baseline discrimination, calibration, and choice-ranking metrics.
3. Define and implement the structured event effect schema for deterministic events.
4. Compare one Global token against late concat while keeping the rest of the network fixed.
5. Compare Post-LN and Pre-LN, including final normalization for Pre-LN.
6. Build trustworthy preference pairs and add ranking loss as an auxiliary objective.
7. Compare `[CLS]` pooling with masked mean or learned pooling queries only if earlier experiments leave
   a measurable limitation.
8. Add stochastic, sequential, and combat-starting event support in source-verifiable stages.

Each step should produce a separately reviewable change and an ablation result. Do not combine data
split, objective, normalization, attention layout, and event-state changes in one experiment.

## Open Decisions

- Whether one combined Global token or several semantic Global tokens performs better.
- Whether final `[CLS]` alone remains sufficient after early global conditioning.
- Whether Pre-LN improves the current shallow network enough to justify a checkpoint-breaking change.
- Which sources provide preference pairs reliable enough for ranking supervision.
- Whether censored-run modeling should remain masked per-Act classification or move to a formal
  discrete-time survival loss.
- How combat-starting event branches obtain a comparable value from the battle AI.
- Which potion and relic-counter fields can be reconstructed reliably from `.run` plus game source.

## Verification Requirements

- Unit tests for canonical item parsing and train/inference encoding parity.
- Tests that snapshots from one run never cross dataset partitions.
- Tests for abandoned-run target validity at each Act boundary.
- Permutation-invariance and variable-length padding tests for the encoder.
- Event contract tests keyed by event ID, phase, and option index.
- Probability calibration evaluated on an untouched run-level test set.
- Ablations reported with fixed seeds and identical data splits.
- End-to-end replay tests comparing predicted option effects with actual game state changes.
