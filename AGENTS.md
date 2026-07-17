具体说明请见 `CLAUDE.md`。

## Working Preferences

- Prefer small, reviewable changes.
- Ask before adding dependencies.
- Always explain what changed and how it was verified.
- If tests cannot run, state why and provide the exact command to run.
- Do not modify secrets, credentials, lockfiles, or generated files unless explicitly requested.

## Set Transformer v2

- The v2 processed dataset lives in `selectcard/processed_data_v2/`; it is generated and must not be committed.
- Rebuild it from `selectcard/` with `python src/data_pipeline.py`.
- Run model tests from `selectcard/src/` with `python -m unittest test_value_network_v2.py`.
- Train from `selectcard/` with `python src/train.py`; use a CUDA-enabled PyTorch environment for the full dataset.
- The default architecture is one early Global token, Pre-LN attention blocks, and final CLS pooling. Ablations are controlled by `GLOBAL_CONDITIONING` and `NORM_POSITION` in `selectcard/src/config.py`.
- v2 checkpoints embed model configuration, the frozen vocabulary, normalization statistics, and preprocessing version. Legacy bare `state_dict` checkpoints are intentionally incompatible and require retraining.
- Do not commit `selectcard/checkpoints/`, `selectcard/processed_data/`, or `selectcard/processed_data_v2/` artifacts.
