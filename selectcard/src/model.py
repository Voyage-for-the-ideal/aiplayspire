import torch
import torch.nn as nn


class SetAttention(nn.Module):
    def __init__(self, d_model, n_heads, dropout=0.1, norm_position="pre"):
        super().__init__()
        if norm_position not in {"pre", "post"}:
            raise ValueError("norm_position must be 'pre' or 'post'")
        self.norm_position = norm_position
        self.mha = nn.MultiheadAttention(
            d_model, n_heads, dropout=dropout, batch_first=True
        )
        self.norm1 = nn.LayerNorm(d_model)
        self.norm2 = nn.LayerNorm(d_model)
        self.ffn = nn.Sequential(
            nn.Linear(d_model, d_model * 4),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_model * 4, d_model),
            nn.Dropout(dropout),
        )

    def forward(self, x, key_padding_mask=None):
        if self.norm_position == "pre":
            norm_x = self.norm1(x)
            attn_out, _ = self.mha(
                norm_x, norm_x, norm_x, key_padding_mask=key_padding_mask
            )
            x = x + attn_out
            return x + self.ffn(self.norm2(x))

        attn_out, _ = self.mha(x, x, x, key_padding_mask=key_padding_mask)
        x = self.norm1(x + attn_out)
        return self.norm2(x + self.ffn(x))


class STSValueNetwork(nn.Module):
    """Permutation-invariant value network for a complete non-combat state."""

    def __init__(
        self,
        vocab_size,
        max_upgrade=15,
        max_count=10,
        d_model=128,
        n_heads=4,
        n_layers=3,
        num_global_features=4,
        dropout=0.1,
        global_conditioning="token",
        norm_position="pre",
    ):
        super().__init__()
        if global_conditioning not in {"token", "late_concat"}:
            raise ValueError("global_conditioning must be 'token' or 'late_concat'")
        self.global_conditioning = global_conditioning
        self.norm_position = norm_position

        self.token_emb = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.upgrade_emb = nn.Embedding(max_upgrade, d_model, padding_idx=0)
        self.count_emb = nn.Embedding(max_count, d_model, padding_idx=0)
        self.cls_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.global_mlp = nn.Sequential(
            nn.Linear(num_global_features, d_model),
            nn.ReLU(),
            nn.Linear(d_model, d_model),
        )
        self.layers = nn.ModuleList(
            [
                SetAttention(
                    d_model, n_heads, dropout=dropout, norm_position=norm_position
                )
                for _ in range(n_layers)
            ]
        )
        self.final_norm = nn.LayerNorm(d_model) if norm_position == "pre" else nn.Identity()
        head_input = d_model * 2 if global_conditioning == "late_concat" else d_model
        self.value_head = nn.Sequential(
            nn.Linear(head_input, d_model // 2),
            nn.ReLU(),
            nn.Linear(d_model // 2, 1),
        )

    def forward(self, seq_tokens, upgrades, counts, global_features):
        batch_size = seq_tokens.size(0)
        x = (
            self.token_emb(seq_tokens)
            + self.upgrade_emb(upgrades)
            + self.count_emb(counts)
        )
        cls_tokens = self.cls_token.expand(batch_size, -1, -1)
        padding_mask = seq_tokens.eq(0)
        context_mask = torch.zeros(
            (batch_size, 1), dtype=torch.bool, device=seq_tokens.device
        )

        global_out = self.global_mlp(global_features)
        if self.global_conditioning == "token":
            x = torch.cat([cls_tokens, global_out.unsqueeze(1), x], dim=1)
            key_padding_mask = torch.cat(
                [context_mask, context_mask, padding_mask], dim=1
            )
        else:
            x = torch.cat([cls_tokens, x], dim=1)
            key_padding_mask = torch.cat([context_mask, padding_mask], dim=1)

        for layer in self.layers:
            x = layer(x, key_padding_mask=key_padding_mask)
        cls_out = self.final_norm(x)[:, 0, :]

        if self.global_conditioning == "late_concat":
            cls_out = torch.cat([cls_out, global_out], dim=-1)
        return self.value_head(cls_out)
