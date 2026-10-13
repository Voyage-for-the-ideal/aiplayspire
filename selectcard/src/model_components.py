"""Optional v4 blocks. Defaults are still the repaired ReLU/LayerNorm model."""

import math

import torch
from torch import nn
from torch.nn import functional as F


def make_norm(width, kind):
    if kind == "layernorm":
        return nn.LayerNorm(width)
    if kind == "rmsnorm":
        return nn.RMSNorm(width, eps=1e-5)
    raise ValueError(f"Unsupported normalization: {kind}")


class SwiGLU(nn.Module):
    def __init__(self, width, dropout):
        super().__init__()
        self.hidden_width = max(8, 8 * round(width / 3))
        self.gate = nn.Linear(width, self.hidden_width)
        self.value = nn.Linear(width, self.hidden_width)
        self.output = nn.Linear(self.hidden_width, width)
        self.dropout = nn.Dropout(dropout)

    def forward(self, x):
        return self.dropout(self.output(self.dropout(F.silu(self.gate(x)) * self.value(x))))


class CoordinatePLR(nn.Module):
    """Raw xy plus eight trainable Fourier frequencies per coordinate."""
    def __init__(self, width, frequencies=8):
        super().__init__()
        self.frequencies = nn.Parameter(torch.randn(2, frequencies))
        self.projection = nn.Linear(2 + 4 * frequencies, width)

    def forward(self, xy):
        angles = 2 * math.pi * xy.unsqueeze(-1) * self.frequencies
        features = torch.cat([xy, angles.sin().flatten(-2), angles.cos().flatten(-2)], dim=-1)
        return F.relu(self.projection(features))


class MapNodeEncoder(nn.Module):
    """Reverse DAG sweep and parameter-matched independent-node control."""
    def __init__(self, width, mode, norm_type="layernorm"):
        super().__init__()
        self.mode = mode
        self.mlp = nn.Sequential(nn.Linear(2 * width, width), nn.GELU(), nn.Linear(width, width))
        self.norm = make_norm(width, norm_type)

    def forward(self, nodes, outgoing, padding):
        batch, _, width = nodes.shape
        z = nodes.reshape(batch, 15, 7, width)
        masks = padding.reshape(batch, 15, 7)
        edge_bits = outgoing.reshape(batch, 15, 7)
        rows = []
        for y in range(14, -1, -1):
            children = torch.zeros_like(z[:, y])
            if self.mode == "dag" and y < 14:
                child_sum = torch.zeros_like(children)
                child_count = nodes.new_zeros((batch, 7, 1))
                for bit, shift in enumerate((-1, 0, 1)):
                    x = torch.arange(7, device=nodes.device)
                    dst = x + shift
                    valid_x = (dst >= 0) & (dst < 7)
                    dst = dst.clamp(0, 6)
                    active = ((edge_bits[:, y] >> bit) & 1).bool()
                    active = active & valid_x & ~masks[:, y + 1, dst]
                    weights = active.unsqueeze(-1).to(nodes.dtype)
                    child_sum = child_sum + rows[-1][:, dst] * weights
                    child_count = child_count + weights
                children = child_sum / child_count.clamp_min(1)
            h = self.norm(z[:, y] + children + self.mlp(torch.cat([z[:, y], children], dim=-1)))
            rows.append(h.masked_fill(masks[:, y].unsqueeze(-1), 0))
        return torch.stack(rows[::-1], dim=1).reshape(batch, 105, width)


class MapLatentPool(nn.Module):
    def __init__(self, width, heads, count, dropout, norm_type):
        super().__init__()
        self.queries = nn.Parameter(torch.randn(1, count, width) * 0.02)
        self.attention = nn.MultiheadAttention(width, heads, dropout=dropout, batch_first=True)
        self.norm = make_norm(width, norm_type)

    def forward(self, nodes, padding):
        absent = padding.all(dim=1)
        # Make one zero key valid only for absent maps, then mask every output.
        safe_mask = padding.clone()
        safe_mask[:, 0] = safe_mask[:, 0] & ~absent
        safe_nodes = nodes.masked_fill(padding.unsqueeze(-1), 0)
        queries = self.queries.expand(nodes.size(0), -1, -1)
        pooled, _ = self.attention(queries, safe_nodes, safe_nodes,
                                   key_padding_mask=safe_mask, need_weights=False)
        pooled = self.norm(queries + pooled)
        pooled = pooled.masked_fill(absent[:, None, None], 0)
        return pooled, absent[:, None].expand(-1, queries.size(1))
