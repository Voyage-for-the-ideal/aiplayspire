# selectcard — format-12 multiscale value network

Follow the root agent guide and `MODEL_V4_DESIGN.md`. Dependencies are in conda `spire`; do not add dependencies, overwrite old artifacts, or run full rebuilding/GPU training without that task being requested.

## Contracts

- Format 12; preprocessing `set-transformer-v11-multiscale`; map context `map-context-v6-persistent-keys`; global features `global-features-v7-floor`; cache 4. Older inputs require rebuilding/retraining.
- Value target `multiscale-hazard-v1`: current progress band uses fine hazards, later bands use coarse 34/51/57 hazards. There are 23 hazard and stopping-position heads plus conditional Heart. Use the shared distribution for training, mechanics probes and inference; ignore inactive heads.
- Raw outcomes are `terminal_known`, `terminal_floor`, `heart_victory`. Heart wins normalize to 57; censored futures never become negative Heart labels or stopping-position targets.
- Global inputs have 11 named fields. Use the feature-name lookup for ascension. Four progress bands and absolute floor distinguish late states.
- Green keys persist across acts and missing later maps. Unknown Sapphire stays unknown before Act IV.
- Preserve 105 map cells, current-candidate semantics, unique final edges, outgoing direction masks, float xy, eight past HISTORY rooms, stable CHARACTER and visible BOSS. Do not inject router plans or change map RNG behavior.
- Preserve item aggregation, order and the 73-item map-model cap. Optional backbone ablations remain off by default. MHA uses `need_weights=False`.
- Freeze resolution, all loss weights, full training-only vocabulary/normalization/priors in checkpoints. Audit prior counts against manifest; invalid old caches must not be reused.

## Commands (from selectcard)

```bash
conda activate spire
python src/rebuild_multiscale.py --input-dir "STS Data"
python src/inspect_multiscale.py --split train --output experiment_results/multiscale-audit.json
python src/train.py --experiment baseline --stage pilot
python src/train.py --experiment multiscale --stage confirm --seed 42 --epochs 10
# Regular job: activate spire via the server's conda.sh, rebuild/audit data, then run five experiments.
sbatch train_multiscale10.sbatch
python src/train.py --evaluate --checkpoint <format12.pth> --output-dir <new-directory>
cd src
python -m unittest test_multiscale test_map_context test_value_network_v2 test_value_network_v3 test_value_network_v4 test_data_pipeline test_api
```

Pilot is fixed 1% run hash, 8 epochs. Confirm is full train/val, 10 epochs, default seed 42. Batch 512, lr 2.8e-4, first three epochs mask map/history. Full rebuilding and GPU jobs remain separate tasks.

Common validation uses reach-17/34/51/57 and Heart with fixed benchmark weights, independent of candidate training loss. Select by common NLL after the raw-floor MAE guard. Bootstrap pairs whole runs and identical seed sets; the declared training budget must be completed. No fixed 20-epoch/three-seed gate. Missing evidence keeps the reference; never deploy automatically or evaluate test without explicit `--evaluation-split test`.

Keep old datasets/checkpoints/results unchanged. Generated artifacts stay ignored. Terminal-deck reconciliation is outside this change and remains a raw-run audit follow-up.
