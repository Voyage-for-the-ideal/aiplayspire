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
