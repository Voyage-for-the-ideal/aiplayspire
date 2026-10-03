# Slay the Spire: Card Selection AI Model

This repository contains an AI-driven system for **Slay the Spire** that predicts and reconstructs the optimal card selection strategy. It features a sophisticated state reconstruction engine ("Time Machine") that tracks deck, relic, and attribute changes throughout a run.

## Features

- **Run Reconstructor**: A "Time Machine" logic that reconstructs the state of each floor (deck, relics, gold, HP) from raw run data files.
- **Data Pipeline**: Automated processing of large-scale Slay the Spire run data (JSON) into a format suitable for Deep Learning.
- **Deep Learning Model**: A neural network designed to evaluate card selection decisions based on the current context (current deck, relics, floor, character, etc.).
- **Continuous Value Target**: Predicts stopping hazards over Boss-aligned 2-3 floor buckets, then scores states by expected terminal floor plus a configurable Heart-win premium.
- **Inference Engine**: Provides real-time or batch recommendations for card picking at any given floor.
- **Mismatch Analysis**: Identifies discrepancies between the AI's predictions and actual player choices to refine the training process.

## Project Structure

- `src/`: Core source code.
  - `reconstructor.py`: The state reconstruction engine.
  - `model.py`: Neural network architecture.
  - `train.py`: Training script for the card selection model.
  - `inference.py`: Logic for making predictions.
  - `data_pipeline.py`: Utilities for data cleaning and transformation.
- `checkpoints/`: (Ignored) Storage for trained model weights.
- `STS Data/`: (Ignored) Raw game data files.
- `processed_data_v2/`: (Ignored) Processed training samples.

## Getting Started

### Prerequisites

- Python 3.8+
- PyTorch
- Pandas, NumPy

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/selectcard.git
   cd selectcard
   ```
2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

### Usage

1. **Data Preparation**: Place your raw `.json` run files in `STS Data/`.
2. **Pilot reconstruction**: validate the pipeline against a bounded sample without
   replacing the current dataset.
   ```bash
   python src/data_pipeline.py --output-dir /tmp/processed_data_v2_pilot \
     --workers 4 --max-files-per-directory 1
   ```
3. **Full reconstruction**: build to a staging directory and replace the old
   generated dataset only after validation succeeds.
   ```bash
   python src/data_pipeline.py --input-dir "STS Data" \
     --output-dir processed_data_v2 --workers 16 --replace
   ```
4. **Training**:
   ```bash
   python src/train.py
   ```
5. **Inference**: Use the `inference.py` to get card selection advice.

### A10+ specialist model

Build a separate dataset containing only Ascension 10 and higher runs, then train
the specialist checkpoint without replacing the general model:

```bash
python src/data_pipeline.py --input-dir "STS Data" \
  --output-dir processed_data_a10plus_v1 --min-ascension 10 --workers 16
python src/train.py --data-dir processed_data_a10plus_v1 \
  --checkpoint-name sts_value_model_a10plus_v1.pth \
  --report-name training_report_a10plus_v1.png
```

This produces `checkpoints/best_sts_value_model_a10plus_v1.pth` and
`checkpoints/sts_value_model_a10plus_v1.pth`. The dataset manifest records the
minimum Ascension, fixed run-ID split seed, and retained run/sample counts by
Ascension. Training balances the Ascension bands represented in the dataset;
the absent lower bands receive zero weight.

The agent continues to use the general checkpoint by default. To explicitly use
the specialist checkpoint for an A10+ run, set `STS_VALUE_CHECKPOINT` before
starting the agent. For example, in PowerShell:

```powershell
$env:STS_VALUE_CHECKPOINT = "D:\code\aiplayspire\selectcard\checkpoints\best_sts_value_model_a10plus_v1.pth"
python -m sts_ai_framework --interval 2.0
```

The A10+ model is trained only on A10–A20 data and is intended for those runs.

### Format 10: Eggs and observable keys

Format-10 checkpoints require preprocessing
`set-transformer-v9-key-observability-eggs` and map context
`map-context-v4-key-observability`. Format 9 checkpoints and older processed
datasets are intentionally rejected. Archived `.run` files do not identify
when the Sapphire key was taken: pre-Act-IV Sapphire state is therefore masked
as unknown, including treasure floors with no recorded relic. Live framework
states keep all three key flags observable.

Build and train this schema on the training server:

```bash
python src/data_pipeline.py --input-dir "STS Data" \
  --output-dir processed_data_keys_eggs_v1 --workers 16
python src/train.py --data-dir processed_data_keys_eggs_v1 \
  --checkpoint-name sts_value_model_keys_eggs_v1.pth \
  --report-name training_report_keys_eggs_v1.png
```

Evaluate the saved best checkpoint without starting another training loop:

```bash
python src/train.py --test-only --data-dir processed_data_keys_eggs_v1 \
  --checkpoint-name sts_value_model_keys_eggs_v1.pth \
  --report-name training_report_keys_eggs_v1.png
```

For a fixed JSON list of inference state payloads, audit the signed marginal
values of all three Eggs and keys:

```bash
python src/audit_value_deltas.py \
  --checkpoint checkpoints/best_sts_value_model_keys_eggs_v1.pth \
  --states-json audit_states.json
```

Each audit state uses the inference payload fields (`deck`, `relics`, `hp`,
`max_hp`, `gold`, `floor`, `ascension`, `character`, `visible_boss`, `act`,
`keys`, and optional map/history fields). The report preserves signed deltas;
negative results are not clipped to zero.

The data pipeline accepts standard, unseeded A0-A20 runs from game builds dated
2020-01-14 or later. It rejects beta/daily/trial/endless runs, unknown content,
invalid telemetry, and runs whose reconstructed final deck or relics do not match
the authoritative run record. `dataset_manifest.json` records rejection reasons,
dataset distributions, content-catalog provenance, and processing throughput.

Vanilla content IDs are loaded from the sibling `cardcrawl/` source tree. The full
16,835-file corpus is CPU and storage bound: use 16-32 CPU cores, 64 GiB RAM, local
NVMe storage with at least 100 GiB free, and at least 200 MB/s sustained storage
throughput (500 MB/s preferred). The GPU is only used by `train.py`.

Run tests from `selectcard/src/` in the `spire` environment:

```bash
python -m unittest test_value_network_v2.py test_data_pipeline.py
```

Processed data and checkpoints from earlier versions are not supported. Rebuild the
dataset and retrain the model when changing to this implementation.

## Credits

This project is part of the **MasterSpire** suite, aiming to create the ultimate Slay the Spire AI agent.
