import torch
import torch.nn as nn

try:
    from .data_contract import VALUE_OUTPUT_DIM
    from .map_context import MAP_GRID_CELLS, HISTORY_HORIZON
except ImportError:
    from data_contract import VALUE_OUTPUT_DIM
    from map_context import MAP_GRID_CELLS, HISTORY_HORIZON


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
                norm_x, norm_x, norm_x, key_padding_mask=key_padding_mask, need_weights=False
            )
            x = x + attn_out
            return x + self.ffn(self.norm2(x))

        attn_out, _ = self.mha(x, x, x, key_padding_mask=key_padding_mask, need_weights=False)
        x = self.norm1(x + attn_out)
        return self.norm2(x + self.ffn(x))


class STSValueNetwork(nn.Module):
    """Permutation-invariant value network for a complete non-combat state.

    v3 map-conditioned extension: when ``use_map_context=True`` the sequence
    gains ``[KEYS]``, ``[MAP x105]`` and ``[HISTORY x8]``
    context tokens (plus a token-type embedding over all positions) and the
    network grows a 3-way key-progress auxiliary head.  With
    ``[CHARACTER]`` is present in both map and non-map variants.
    """

    NUM_ITEM_ROOMS = 8          # 0 = pad, 1..7 = M/E/R/$/T/?/B
    NUM_CONTEXT_TYPES = 8       # cls, global, character, boss, keys, map, history, item
    HISTORY_DIST_PAD = HISTORY_HORIZON  # distance id for missing history slots
    HISTORY_TYPE_ID = 6
    ITEM_TYPE_ID = 7

    def __init__(
        self,
        vocab_size,
        max_upgrade=15,
        max_count=10,
        d_model=128,
        n_heads=4,
        n_layers=3,
        num_global_features=9,
        num_characters=4,
        num_bosses=12,
        dropout=0.1,
        global_conditioning="token",
        norm_position="pre",
        use_map_context=False,
        num_key_features=9,
        ffn_type="relu",
        norm_type="layernorm",
        map_encoder="none",
        map_latents=0,
        coordinate_embedding="linear",
        pooling="cls",
        register_tokens=0,
        item_token_budget=None,
    ):
        super().__init__()
        self.model_config = {k: v for k, v in locals().items() if k not in {"self", "__class__"}}
        self.item_token_budget = item_token_budget if item_token_budget is not None else (73 if use_map_context else 192)
        self.model_config["item_token_budget"] = self.item_token_budget
        if (ffn_type, norm_type, map_encoder, map_latents, coordinate_embedding, pooling, register_tokens) != (
            "relu", "layernorm", "none", 0, "linear", "cls", 0
        ):
            raise ValueError("Optional architecture components are not implemented yet")
        if isinstance(self.item_token_budget, bool) or not isinstance(self.item_token_budget, int) or self.item_token_budget <= 0:
            raise ValueError("item_token_budget must be a positive integer")
        if use_map_context and self.item_token_budget != 73:
            raise ValueError("V4 map models retain the 73-item contract")
        if global_conditioning not in {"token", "late_concat"}:
            raise ValueError("global_conditioning must be 'token' or 'late_concat'")
        self.global_conditioning = global_conditioning
        self.norm_position = norm_position
        self.use_map_context = use_map_context

        self.token_emb = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.upgrade_emb = nn.Embedding(max_upgrade, d_model, padding_idx=0)
        self.count_emb = nn.Embedding(max_count, d_model, padding_idx=0)
        self.cls_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.character_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.character_emb = nn.Embedding(num_characters, d_model)
        self.boss_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.boss_emb = nn.Embedding(num_bosses, d_model)
        self.global_mlp = nn.Sequential(
            nn.Linear(num_global_features, d_model),
            nn.ReLU(),
            nn.Linear(d_model, d_model),
        )

        if use_map_context:
            self.token_type_emb = nn.Embedding(self.NUM_CONTEXT_TYPES, d_model)
            self.map_room_emb = nn.Embedding(self.NUM_ITEM_ROOMS, d_model, padding_idx=0)
            self.map_flag_proj = nn.Linear(7, d_model)
            self.hist_dist_emb = nn.Embedding(
                HISTORY_HORIZON + 1, d_model, padding_idx=self.HISTORY_DIST_PAD
            )
            self.keys_mlp = nn.Sequential(
                nn.Linear(num_key_features, d_model),
                nn.ReLU(),
                nn.Linear(d_model, d_model),
            )
            self.key_head = nn.Linear(d_model, 3)
            # Row-major (y, x) coordinate pair per grid cell, index = y*7+x.
            coordinates = torch.stack(
                (
                    torch.linspace(0.0, 1.0, 7).repeat(15),
                    torch.linspace(0.0, 1.0, 15).repeat_interleave(7),
                ),
                dim=1,
            )
            self.register_buffer("map_cell_coordinates", coordinates, persistent=False)

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
            nn.Linear(d_model // 2, VALUE_OUTPUT_DIM),
        )

    def _decode_map_flags(self, map_flags):
        """Bit-packed cell flags -> [B, 105, 7] float feature matrix."""
        burning = ((map_flags >> 1) & 1).float().unsqueeze(-1)
        current_candidate = ((map_flags >> 2) & 1).float().unsqueeze(-1)
        reachable = ((map_flags >> 3) & 1).float().unsqueeze(-1)
        out_deg = ((map_flags >> 4) & 3).float().div(3.0).unsqueeze(-1)
        in_deg = ((map_flags >> 6) & 3).float().div(3.0).unsqueeze(-1)
        coordinates = (
            self.map_cell_coordinates.to(device=map_flags.device, dtype=torch.float32)
            .unsqueeze(0)
            .expand(map_flags.size(0), -1, -1)
        )
        return torch.cat(
            [burning, current_candidate, reachable, coordinates, out_deg, in_deg],
            dim=-1,
        )

    def _context_tokens(self, keys_features, map_rooms, map_flags,
                        history_rooms, history_dist, batch_size, device):
        """Build the [KEYS]/[MAP]/[HISTORY] token block plus its mask."""
        if keys_features is None:
            keys_features = torch.zeros((batch_size, self.keys_mlp[0].in_features),
                                        device=device)
        if map_rooms is None:
            map_rooms = torch.zeros((batch_size, MAP_GRID_CELLS), dtype=torch.long, device=device)
        if map_flags is None:
            map_flags = torch.zeros((batch_size, MAP_GRID_CELLS), dtype=torch.long, device=device)
        if history_rooms is None:
            history_rooms = torch.zeros((batch_size, HISTORY_HORIZON), dtype=torch.long,
                                        device=device)
        if history_dist is None:
            history_dist = torch.full((batch_size, HISTORY_HORIZON), self.HISTORY_DIST_PAD,
                                      dtype=torch.long, device=device)
        map_flags = map_flags.to(device=device, dtype=torch.long)
        map_rooms = map_rooms.to(device=device, dtype=torch.long)
        history_rooms = history_rooms.to(device=device, dtype=torch.long)
        history_dist = history_dist.to(device=device, dtype=torch.long)

        keys_out = self.keys_mlp(keys_features.to(device)).unsqueeze(1) + self.token_type_emb(
            torch.full((batch_size, 1), 4, dtype=torch.long, device=device)
        )
        map_out = (
            self.map_room_emb(map_rooms)
            + self.map_flag_proj(self._decode_map_flags(map_flags))
            + self.token_type_emb(torch.full_like(map_rooms, 5))
        )
        hist_out = (
            self.map_room_emb(history_rooms)
            + self.hist_dist_emb(history_dist)
            + self.token_type_emb(torch.full_like(history_rooms, self.HISTORY_TYPE_ID))
        )
        keys_mask = torch.zeros((batch_size, 1), dtype=torch.bool, device=device)
        map_mask = map_rooms.eq(0)
        hist_mask = history_dist.eq(self.HISTORY_DIST_PAD)
        return keys_out, map_out, hist_out, keys_mask, map_mask, hist_mask

    def forward(
        self,
        seq_tokens,
        upgrades,
        counts,
        global_features,
        character_ids,
        boss_ids,
        keys_features=None,
        map_rooms=None,
        map_flags=None,
        history_rooms=None,
        history_dist=None,
        return_keys=False,
        map_outgoing_mask=None,
    ):
        batch_size = seq_tokens.size(0)
        device = seq_tokens.device
        x = (
            self.token_emb(seq_tokens)
            + self.upgrade_emb(upgrades)
            + self.count_emb(counts)
        )
        cls_tokens = self.cls_token.expand(batch_size, -1, -1)
        character_context = (
            self.character_token.expand(batch_size, -1, -1)
            + self.character_emb(character_ids).unsqueeze(1)
        )
        boss_context = self.boss_token.expand(batch_size, -1, -1) + self.boss_emb(boss_ids).unsqueeze(1)
        padding_mask = seq_tokens.eq(0)
        context_mask = torch.zeros(
            (batch_size, 1), dtype=torch.bool, device=device
        )

        global_out = self.global_mlp(global_features)
        if not self.use_map_context:
            if self.global_conditioning == "token":
                x = torch.cat([cls_tokens, global_out.unsqueeze(1), character_context, boss_context, x], dim=1)
                key_padding_mask = torch.cat(
                    [context_mask, context_mask, context_mask, context_mask, padding_mask], dim=1
                )
            else:
                x = torch.cat([cls_tokens, character_context, boss_context, x], dim=1)
                key_padding_mask = torch.cat(
                    [context_mask, context_mask, context_mask, padding_mask], dim=1
                )
        else:
            item_type = self.token_type_emb(
                torch.zeros_like(seq_tokens).fill_(self.ITEM_TYPE_ID)
            )
            x = x + item_type
            cls_out = self.cls_token.expand(batch_size, -1, -1) + self.token_type_emb(
                torch.zeros((batch_size, 1), dtype=torch.long, device=device)
            )
            global_token = global_out.unsqueeze(1) + self.token_type_emb(
                torch.full((batch_size, 1), 1, dtype=torch.long, device=device)
            )
            character_token = character_context + self.token_type_emb(
                torch.full((batch_size, 1), 2, dtype=torch.long, device=device)
            )
            boss_token = boss_context + self.token_type_emb(
                torch.full((batch_size, 1), 3, dtype=torch.long, device=device)
            )
            keys_out, map_out, hist_out, keys_mask, map_mask, hist_mask = (
                self._context_tokens(
                    keys_features, map_rooms, map_flags,
                    history_rooms, history_dist, batch_size, device,
                )
            )
            if self.global_conditioning == "token":
                x = torch.cat(
                    [cls_out, global_token, character_token, boss_token, keys_out,
                     map_out, hist_out, x],
                    dim=1,
                )
                key_padding_mask = torch.cat(
                    [context_mask, context_mask, context_mask, context_mask,
                     keys_mask, map_mask, hist_mask, padding_mask],
                    dim=1,
                )
            else:
                x = torch.cat(
                    [cls_out, character_token, boss_token, keys_out, map_out, hist_out, x], dim=1
                )
                key_padding_mask = torch.cat(
                    [context_mask, context_mask, context_mask, keys_mask,
                     map_mask, hist_mask, padding_mask],
                    dim=1,
                )

        for layer in self.layers:
            x = layer(x, key_padding_mask=key_padding_mask)
        cls_out = self.final_norm(x)[:, 0, :]

        if self.global_conditioning == "late_concat":
            cls_out = torch.cat([cls_out, global_out], dim=-1)
        logits = self.value_head(cls_out)
        if return_keys:
            if not self.use_map_context:
                raise ValueError("key head requires use_map_context=True")
            return logits, self.key_head(cls_out)
        return logits
