# Slay the Spire: Card Selection AI Model

This repository contains an AI-driven system for **Slay the Spire** that predicts and reconstructs the optimal card selection strategy. It features a sophisticated state reconstruction engine ("Time Machine") that tracks deck, relic, and attribute changes throughout a run.

## Features

- **Run Reconstructor**: A "Time Machine" logic that reconstructs the state of each floor (deck, relics, gold, HP) from raw run data files.
- **Data Pipeline**: Automated processing of large-scale Slay the Spire run data (JSON) into a format suitable for Deep Learning.
- **Deep Learning Model**: A neural network designed to evaluate card selection decisions based on the current context (current deck, relics, floor, character, etc.).
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
- `processed_data/`: (Ignored) Processed training samples.

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
2. **Reconstruction**: Run the data pipeline to generate training samples.
   ```bash
   python src/data_pipeline.py
   ```
3. **Training**:
   ```bash
   python src/train.py
   ```
4. **Inference**: Use the `inference.py` to get card selection advice.

## Credits

This project is part of the **MasterSpire** suite, aiming to create the ultimate Slay the Spire AI agent.
