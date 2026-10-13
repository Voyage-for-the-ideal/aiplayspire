# selectcard — V4 value network

Use the root agent guide. Python dependencies are already in conda `spire`; do not add dependencies, commit generated datasets/checkpoints, or start full training without the task requiring it.

## Contracts and architecture

- V4 repairs coordinates, current-position semantics and graph packing before testing architecture changes.
- Preprocessing: `set-transformer-v10-current-edges`; map context: `map-context-v5-current-edges`; checkpoint format **11**. Older data/checkpoints require rebuilding/retraining.
- Main value target stays `bucket-hazard-v1`: 20 stopping-hazard outputs and a conditional Heart output. Inference uses expected terminal floor plus the Heart premium.
- Default configured backbone: d_model=192, layers=5, heads=4, ReLU FFN, LayerNorm, pre-LN, early global token, CLS pooling. MHA uses `need_weights=False`.
- Unique `(item name, upgrade)` records retain count embeddings; item order and the **73-item map-model cap** are unchanged.
- Map grid: 105 cells, row-major `y*7+x`; float coordinates `[x/6,y/14]`. Bit 2 means `current_candidate`, not historical visited.
- Both archived and live contexts normalize unique final edges, derive true in/out degrees and reachability, and pack left/straight/right next-row edges into `map_outgoing_mask`. Row 14 connects to a virtual Boss, out-degree 1 and ordinary outgoing mask 0.
- Archived positions are candidate sets from observed prefixes. Live positions are exact when known. Unknown position has all-zero current flags. HISTORY retains eight already-left rooms from this act, most recent first.
- Keys keep value-plus-known-mask observability. Unknown Sapphire is not a negative label. Auxiliary key prediction reconstructs current known key state.
- Experimental FFNs, RMSNorm, edges/DAG, 16/32 latents, PLR, CLS+item-mean and registers default off. Checkpoints freeze every architecture choice and the item budget.
- DAG uses reverse row order with successor aggregation and a shared residual MLP. Independent-node MLP is parameter matched. Empty maps are safely masked during latent pooling.
- See root `MODEL_V4_DESIGN.md` for rationale, experimental protocol and migration.

## Commands (from selectcard)

```bash
conda activate spire
python src/data_pipeline.py --input-dir "STS Data" --output-dir processed_data_v4
python src/train.py --list-experiments
python src/train.py --experiment baseline --stage pilot --data-dir processed_data_v4
python src/train.py --experiment baseline --stage confirm --seed 42 --data-dir processed_data_v4
python src/train.py --evaluate --checkpoint checkpoints/v4/baseline/confirm/42/sts_value_model_final.pth --data-dir processed_data_v4 --output-dir experiment_results/baseline-42
python src/benchmark_v4.py --experiment baseline --device cuda --compare-attention --output experiment_results/benchmark-cuda.json
uvicorn src.api:app
cd src
python -m unittest test_map_context.py test_value_network_v2.py test_value_network_v3.py test_value_network_v4.py test_data_pipeline.py test_api.py
```

Default CLI stage is pilot: deterministic 1% run hash, seed 42, 8 epochs. Confirm uses full data, 20 epochs, seeds 42/43/44. Batch remains 512, AdamW lr 2.8e-4, first three epochs mask map/history. Priors, normalization and vocabulary share a full-training-only cache across candidates. No automatic test-set evaluation; test requires explicit `--evaluation-split test` after model selection.

`--test-only` aliases read-only evaluation and requires `--checkpoint` plus a separate output directory. It is a boolean flag, not a checkpoint-path option. Evaluation loads frozen checkpoint configuration, emits grouped metrics and run-level comparison exports, and never rewrites the source checkpoint.

Use `evaluation_v4.py --baseline ... --candidate ... --output ...` with paired validation exports. Promotion requires completed full-data 20-epoch confirmation across all three seeds, a strictly negative upper 95% run-cluster-bootstrap NLL difference, and no significant mechanism-ordering regression. Insufficient evidence keeps the baseline. No automatic deployment.

The model's `STSInferenceEngine` simulates candidate next states for framework/API decisions. Keep vanilla content validation, state timing, character schema, unknown-key masks and the scalar value semantics consistent across training and inference.

Do not modify the map generator to clean up duplicate parents: its behavior mirrors the game. Derive graph features from final unique edges instead. Generated V4 data, checkpoints, caches and experiment reports stay ignored.
