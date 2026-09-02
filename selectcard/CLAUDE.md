# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trains a **Set Transformer bucket-hazard value network** that evaluates Slay the Spire game states. Used by `sts_ai_framework` for card/relic/event/shop decisions. The model predicts monotone progress survival across 20 two-to-three-floor buckets plus conditional Heart victory; inference composes them into expected terminal floor with a configurable Heart premium.

## Common Commands

```bash
# Process raw JSON run data into training samples (Parquet chunks)
python src/data_pipeline.py

# Train the survival-value network
python src/train.py

# Run inference API server
uvicorn src.api:app --reload

# Test reconstructor accuracy (matches simulated deck vs master_deck)
python src/test_reconstructor.py

# Run model tests
cd src && python -m unittest test_value_network_v2.py

# Find reconstructor mismatches for debugging
python src/find_mismatches.py

# Inspect label distribution in processed Parquet data
python src/inspect_labels.py
```

No `requirements.txt` exists in this directory — dependencies are PyTorch, pandas, FastAPI, uvicorn, scikit-learn, matplotlib (see parent project).

## Architecture: Data Flow

```
STS Data/*.json.gz  →  data_pipeline.py  →  processed_data_v2/*.parquet  →  train.py
         │                    │                                              │
    raw run history    RunReconstructor                              STSValueNetwork
                       replays each floor                            + STSDataset
                       generates (state, label) pairs
```

### Data Pipeline (`data_pipeline.py`)

`process_file()` reads `.json`/`.json.gz` archives and isolates failures per run. The pipeline performs cheap mode/version checks, validates all logged content against the vanilla Java source catalog, replays the run, then requires exact final deck and relic reconstruction. Accepted samples are streamed into split/validity-partitioned Parquet chunks via a bounded `ProcessPoolExecutor`.

**Filters applied in `data_pipeline.validate_raw_run()`:**
- Standard unseeded, non-beta A0-A20 runs only; Daily, Trial, Endless, and special-seed runs are excluded
- Both `local_time` and `build_version` must be at least 2020-01-14
- Characters, cards, relics, potions, shop items, and enemies must resolve to the vanilla source catalog
- PrismaticShard, malformed telemetry, missing enemy history, and unknown content are excluded
- Final reconstructed deck and relic multisets must exactly match `master_deck` and `relics`
- Abandoned runs are retained as censored: observed bucket progress remains valid and unresolved future buckets do not train

**Labeling scheme**:
- Bucket endpoints are `3,6,9,12,15,17,20,23,26,29,32,34,37,40,43,46,49,51,54,57`.
- Each output is the conditional risk of stopping before its endpoint. Completed buckets are masked, reached future buckets have hazard target 0, and only the first known unreached bucket has target 1.
- Heart victories are normalized to progress 57 and receive a separate terminal label; ordinary victories stop progress at their recorded Act 3 endpoint.
- The cumulative survival curve is `S_i = product(1 - h_j)`, so farther progress can never be more likely than nearer progress.

The scalar value is expected terminal floor plus a configurable Heart-win bonus measured in equivalent floors. The default bonus is 3; 6 can be evaluated with the same checkpoint.

### Reconstructor (`reconstructor.py`)

`RunReconstructor` replays a run floor-by-floor to reconstruct deck, relics, HP, gold at each decision point from end-of-run JSON. Critical design patterns:

**Two-Pass Diff Reconciler** (`_reconcile_all_diffs()`): First does a dry-run replay, then compares simulated deck vs `master_deck` (the authoritative end-of-run deck from JSON). Any mismatches are classified:

1. **Phase 1 — Implicit upgrades**: If excess has base card X and missing has upgraded X+, it was a silent upgrade (War Paint, Whetstone, etc.) not recorded in event logs.
2. **Phase 2 — Capacity-constrained removals**: Empty Cage (2 removes), Astrolabe (3 transform), Pandora's Box (all Strikes/Defends transform) — matched by relic floor timing.
3. **Phase 3 — Neow black-box**: TRANSFORM_TWO_CARDS, REMOVE_TWO, CURSE cost, etc. at floor 0.

Implicit changes are stored in `_implicit_removals[floor]` / `_implicit_additions[floor]` and applied during actual replay.

**Egg relics** (Molten/Frozen/Toxic Egg): Already handled upstream — `master_deck` records the upgraded name, so the reconstructor doesn't need special egg logic. The `_handle_egg_upgrade()` method exists but is a no-op by design.

**Card naming convention**: `AbstractCard.getMetricID()` produces `"CardID"` (base), `"CardID+"` (upgraded once, NOT `+1`), or `"CardID+N"` for multi-upgrade cards like Searing Blow.

### Model (`model.py`)

`STSValueNetwork` — a permutation-invariant Set Transformer:

- **Input encoding**: `Token = ID_Embedding + Upgrade_Embedding + Count_Embedding` (three embeddings summed per unique item)
- **No positional encoding** — the deck is an unordered set; permutation invariance is intentional
- **[CLS] token** prepended to the sequence, pooled after transformer layers
- **Global features** (floor, HP, gold, ascension) processed through a separate MLP, then concatenated with [CLS] output
- **Output**: 20 stopping-hazard logits plus one conditional Heart logit; cumulative products produce monotone survival probabilities

`SetAttention` is a standard transformer block (MHA + FFN with residual connections), applied over the set dimension.

### Dataset (`dataset.py`)

`STSDataset` lazily loads pre-partitioned valid Parquet chunks via LRU cache (max 16 chunks in memory). It uses binary search (`bisect`) to map global index → (chunk file, local index), without retaining one Python index per sample. Training shuffles bounded chunks and applies inverse-frequency BCE weights so A0, A1-5, A6-10, A11-15, A16-19, and A20 contribute equally.

The vocabulary and global-feature normalization are fitted from the training split, frozen, and embedded in each checkpoint. Older data and checkpoint formats are unsupported and must be rebuilt or retrained.

**Key detail**: Items are aggregated by `(base_name, upgrade_level)` before tokenization, so 5× Strike_R become one token with count=5. This keeps sequence length manageable (~40-60 tokens vs 200+).

### Inference Engine (`inference.py`)

`STSInferenceEngine` loads the trained model and provides:

- **`_apply_choice(state, choice)`**: Simulates applying a choice to a state — handles card picks, buys, removes, upgrades, rests, composite events with multiple effects. Omamori-aware curse blocking.
- **`recommend_choice(state, choices)`**: Evaluates each choice by simulating the resulting state and scoring via `evaluate_state()`. For choices with unknown purge targets (e.g., "remove a card"), it tries removing each unique card in deck and picks the best.
- **`shop_greedy_search(state, goods)`**: Iteratively buys the single item with the highest marginal V(state) improvement, repeating until nothing improves the score.

Global-feature transforms and fitted caps are stored in the checkpoint and reused during inference. Ascension is encoded as `level / 20`, so A0=0 and A20=1.

### API (`api.py`)

FastAPI server with two POST endpoints:
- `/recommend/choice` — Given `state` + `choices[]`, returns the best choice
- `/recommend/shop` — Given `state` + `goods[]`, returns greedy buy list

Pydantic models: `PlayerState`, `Choice`, `RecommendationRequest`, `ShopRequest`.

### Config (`config.py`)

Central hyperparameter store: `BATCH_SIZE=64`, `EPOCHS=10`, `LR=1e-4`, `D_MODEL=128`, `N_HEADS=4`, `N_LAYERS=3`, `DROPOUT=0.1`, `VOCAB_BUFFER=1000`, `MAX_UPGRADE=15`, `MAX_COUNT=10`.

## Hardcoded Paths to Be Aware Of

- `test_reconstructor.py` and `find_mismatches.py` reference `D:\code\aiplayspire\selectcard\STS Data` — a same-repo path
- `sts_ai_framework/llm_agent.py` imports from `../selectcard` — same-repo dependency
- `data_pipeline.py` uses `ProcessPoolExecutor` (not ThreadPoolExecutor) — the `process_file` function must be importable at module level for pickling
