"""Parse per-epoch loss from the training log and plot the learning curves."""
import re
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

LOG = r"D:/code/aiplayspire/selectcard/checkpoints/train_44002.log"
OUT = r"D:/code/aiplayspire/selectcard/checkpoints/training_report_v6.png"

epochs, train_losses, val_losses = [], [], []
pattern = re.compile(r"Epoch (\d+)/\d+: train_loss=([\d.]+) val_loss=([\d.]+)")
with open(LOG, encoding="utf-8") as handle:
    for line in handle:
        match = pattern.search(line)
        if match:
            epochs.append(int(match.group(1)))
            train_losses.append(float(match.group(2)))
            val_losses.append(float(match.group(3)))

# Recompute the cosine LR schedule from config values (train.py cosine_decay).
base_lr, decay = 2.8e-4, 20
learning_rates = [
    base_lr * 0.5 * (1 + np.cos(np.pi * min(epoch, decay) / decay))
    for epoch in epochs
]

print("epoch | train_loss | val_loss | lr")
for epoch, train, val, lr in zip(epochs, train_losses, val_losses, learning_rates):
    print(f"{epoch:5d} | {train:.4f} | {val:.4f} | {lr:.2e}")

best_index = min(range(len(val_losses)), key=val_losses.__getitem__)
print(f"\nbest val_loss = {val_losses[best_index]:.4f} at epoch {epochs[best_index]}")
print(f"val loss at epoch 1 = {val_losses[0]:.4f}")

figure, (loss_axis, lr_axis) = plt.subplots(1, 2, figsize=(14, 5))
loss_axis.plot(epochs, train_losses, marker="o", label="Train Loss (weighted)")
loss_axis.plot(epochs, val_losses, marker="o", label="Validation Loss")
loss_axis.scatter(
    [epochs[best_index]], [val_losses[best_index]], color="tab:red",
    marker="*", s=160, zorder=3,
    label=f"Best Validation Loss (Epoch {epochs[best_index]})",
)
loss_axis.set(title="Loss History (from train_44002.log)", xlabel="Epoch", ylabel="Loss")
loss_axis.legend()
loss_axis.grid(alpha=0.25)

lr_axis.plot(epochs, learning_rates, color="green", marker="s", label="Learning Rate (recomputed)")
lr_axis.set(title="Cosine Learning Rate Schedule", xlabel="Epoch", ylabel="Learning Rate")
lr_axis.legend()
lr_axis.grid(alpha=0.25)

figure.tight_layout()
figure.savefig(OUT, dpi=150)
plt.close(figure)
print(f"\nsaved plot to {OUT}")
