import torch
from torch import nn
from selectcard.src.model import SetAttention
from .contracts import SCALARS
from .encoding import WIDTH

DEFAULT_CONFIG = dict(d_model=192, layers=5, heads=4, dropout=.1)


class PolicyEncoder(nn.Module):
    def __init__(self, vocab_size, d_model=192, layers=5, heads=4, dropout=.1):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.numeric = nn.Linear(WIDTH, d_model)
        self.global_mlp = nn.Sequential(nn.Linear(len(SCALARS) * 2, d_model), nn.ReLU(), nn.Linear(d_model, d_model))
        self.cls = nn.Parameter(torch.randn(1, 1, d_model) * .02)
        self.layers = nn.ModuleList(SetAttention(d_model, heads, dropout, norm_position="pre") for _ in range(layers))
        self.norm = nn.LayerNorm(d_model)

    def forward(self, batch):
        tokens = self.embedding(batch["ids"]) + self.numeric(batch["nums"])
        context = torch.cat((self.cls.expand(len(tokens), -1, -1), self.global_mlp(batch["globals"]).unsqueeze(1), tokens), dim=1)
        mask = torch.cat((torch.zeros((len(tokens), 2), dtype=torch.bool, device=tokens.device), batch["ids"].eq(0)), dim=1)
        for layer in self.layers:
            context = layer(context, key_padding_mask=mask)
        return self.norm(context[:, 0])


class SetPolicy(nn.Module):
    def __init__(self, vocab_size, **config):
        super().__init__()
        self.config = {**DEFAULT_CONFIG, **config}
        d = self.config["d_model"]
        self.encoder = PolicyEncoder(vocab_size, **self.config)
        self.action_numeric = nn.Linear(WIDTH, d)
        self.head = nn.Sequential(nn.Linear(d * 2, d), nn.ReLU(), nn.Linear(d, 1))

    def forward(self, batch):
        if not batch["legal"].any(dim=1).all():
            raise ValueError("all-illegal candidate row")
        state = self.encoder(batch)
        ids = batch["candidate_ids"]
        present = ids.ne(0).unsqueeze(-1)
        action = (self.encoder.embedding(ids) * present).sum(dim=2) / present.sum(dim=2).clamp_min(1)
        action = action + self.action_numeric(batch["candidate_nums"])
        logits = self.head(torch.cat((state[:, None].expand_as(action), action), dim=-1)).squeeze(-1)
        return logits.masked_fill(~batch["legal"], -torch.inf)


class HeartValue(nn.Module):
    """Independent weights/optimizer, including its own set encoder."""
    def __init__(self, vocab_size, **config):
        super().__init__()
        self.encoder = PolicyEncoder(vocab_size, **{**DEFAULT_CONFIG, **config})
        self.head = nn.Linear(config.get("d_model", 192), 1)

    def forward(self, batch):
        return torch.sigmoid(self.head(self.encoder(batch))).squeeze(-1)
