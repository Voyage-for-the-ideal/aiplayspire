# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trains a **Set Transformer survival-value network** that evaluates Slay the Spire game states. Used by `sts_ai_framework` for card/relic/event/shop decisions. The model outputs a single scalar: probability of surviving the current act.

## Common Commands

```bash
# Process raw JSON run data into training samples (Parquet chunks)
python src/data_pipeline.py

# Train the survival-value network
python src/train.py

# Run inference API server
uvicorn src.api:app --reload

# Build vocab without full training (faster, uses DataLoader pass)
python build_vocab_fast.py

# Test reconstructor accuracy (matches simulated deck vs master_deck)
python src/test_reconstructor.py

# Find reconstructor mismatches for debugging
python src/find_mismatches.py

# Inspect label distribution in processed Parquet data
python src/inspect_labels.py
```

No `requirements.txt` exists in this directory — dependencies are PyTorch, pandas, FastAPI, uvicorn, scikit-learn, matplotlib (see parent project).

## Architecture: Data Flow

```
STS Data/*.json.gz  →  data_pipeline.py  →  processed_data/*.parquet  →  train.py
         │                    │                                              │
    raw run history    RunReconstructor                              STSValueNetwork
                       replays each floor                            + STSDataset
                       generates (state, label) pairs
```

### Data Pipeline (`data_pipeline.py`)

`process_file()` reads `.json`/`.json.gz` files (each containing one or more runs), instantiates `RunReconstructor` per run, validates via `validate_run()`, then calls `replay()` which yields one snapshot per card-choice event. Samples are batched into Parquet chunks (50k rows each) via `ProcessPoolExecutor`.

**Filters applied in `RunReconstructor.validate_run()`:**
- A15+ only (`ascension >= 15`)
- Excludes abandoned runs (`victory: false` without `killed_by`)
- Excludes PrismaticShard runs
- Excludes non-vanilla characters (must be IRONCLAD/THE_SILENT/DEFECT/WATCHER)
- Excludes runs where simulated deck diverges from `master_deck` by >10 cards (or >0 with shop visit)

**Labeling scheme** (in `data_pipeline.py` lines 49-54):
- Floor ≤16: label=1 if run reached floor >16 (survived Act 1 boss)
- Floor 17-33: label=1 if run reached floor >33 (survived Act 2 boss)
- Floor 34+: label=1 if run reached floor ≥50 (survived Act 3 boss / heart)

This is act-survival, not whole-run victory/loss. The model learns "will I survive this act" rather than "will I win the run."

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
- **Output**: single logit → sigmoid → survival probability (0–1)

`SetAttention` is a standard transformer block (MHA + FFN with residual connections), applied over the set dimension.

### Dataset (`dataset.py`)

`STSDataset` lazily loads Parquet chunks via LRU cache (max 16 chunks in memory). Uses binary search (`bisect`) to map global index → (chunk file, local index). Computes standardization stats (mean/std for HP, gold) once at init by scanning all chunks.

`SimpleTokenizer` is built on-the-fly during training — `train.py` saves it to `checkpoints/vocab.json` afterward. For inference, load it via `InferenceTokenizer`.

**Key detail**: Items are aggregated by `(base_name, upgrade_level)` before tokenization, so 5× Strike_R become one token with count=5. This keeps sequence length manageable (~40-60 tokens vs 200+).

### Inference Engine (`inference.py`)

`STSInferenceEngine` loads the trained model and provides:

- **`_apply_choice(state, choice)`**: Simulates applying a choice to a state — handles card picks, buys, removes, upgrades, rests, composite events with multiple effects. Omamori-aware curse blocking.
- **`recommend_choice(state, choices)`**: Evaluates each choice by simulating the resulting state and scoring via `evaluate_state()`. For choices with unknown purge targets (e.g., "remove a card"), it tries removing each unique card in deck and picks the best.
- **`shop_greedy_search(state, goods)`**: Iteratively buys the single item with the highest marginal V(state) improvement, repeating until nothing improves the score.

**Hardcoded normalization constants** (must match training): `floor/55.0`, `ascension/20.0`, HP `(x - 50.2277) / 158.0118`, gold `(x - 222.6476) / 3719.5747`.

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
