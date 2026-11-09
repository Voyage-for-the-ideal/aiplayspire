try:
    from .multiscale import ValueOutputs
except ImportError:
    from multiscale import ValueOutputs

import torch
import torch.nn as nn

try:
    from .data_contract import VALUE_OUTPUT_DIM
    from .map_context import MAP_GRID_CELLS, HISTORY_HORIZON
    from .model_components import make_norm, SwiGLU, CoordinatePLR, MapNodeEncoder, MapLatentPool
except ImportError:
    from data_contract import VALUE_OUTPUT_DIM
    from map_context import MAP_GRID_CELLS, HISTORY_HORIZON
    from model_components import make_norm, SwiGLU, CoordinatePLR, MapNodeEncoder, MapLatentPool


class SetAttention(nn.Module):
    def __init__(self, d_model, n_heads, dropout=0.1, norm_position="pre",
                 ffn_type="relu", norm_type="layernorm"):
        super().__init__()
        if norm_position not in {"pre", "post"}:
            raise ValueError("norm_position must be 'pre' or 'post'")
        if ffn_type not in {"relu", "gelu", "swiglu"}:
            raise ValueError("Unsupported FFN type")
        self.norm_position = norm_position
        self.mha = nn.MultiheadAttention(d_model, n_heads, dropout=dropout, batch_first=True)
        self.norm1, self.norm2 = make_norm(d_model, norm_type), make_norm(d_model, norm_type)
        if ffn_type == "swiglu":
            self.ffn = SwiGLU(d_model, dropout)
        else:
            self.ffn = nn.Sequential(
                nn.Linear(d_model, d_model * 4),
                nn.ReLU() if ffn_type == "relu" else nn.GELU(),
                nn.Dropout(dropout), nn.Linear(d_model * 4, d_model), nn.Dropout(dropout),
            )

    def forward(self, x, key_padding_mask=None):
        source = self.norm1(x) if self.norm_position == "pre" else x
        attn_out, _ = self.mha(source, source, source,
                              key_padding_mask=key_padding_mask, need_weights=False)
        if self.norm_position == "pre":
            x = x + attn_out
            return x + self.ffn(self.norm2(x))
        x = self.norm1(x + attn_out)
        return self.norm2(x + self.ffn(x))


class STSValueNetwork(nn.Module):
    """V4 set value model; experimental components are opt-in and checkpointed."""
    NUM_ITEM_ROOMS = 8
    NUM_CONTEXT_TYPES = 8
    HISTORY_DIST_PAD = HISTORY_HORIZON
    HISTORY_TYPE_ID = 6
    ITEM_TYPE_ID = 7

    def __init__(self, vocab_size, max_upgrade=15, max_count=10, d_model=128,
                 n_heads=4, n_layers=3, num_global_features=11, num_characters=4,
                 num_bosses=12, dropout=0.1, global_conditioning="token",
                 norm_position="pre", use_map_context=False, num_key_features=9,
                 ffn_type="relu", norm_type="layernorm", map_encoder="none",
                 map_latents=0, coordinate_embedding="linear", pooling="cls",
                 register_tokens=0, item_token_budget=None, resolution_mode="multiscale",
                 ablate_map_history=False):
        super().__init__()
        if resolution_mode not in {"full_fine", "multiscale"}:
            raise ValueError("Invalid resolution mode")
        if ablate_map_history and not use_map_context:
            raise ValueError("map/history ablation requires map context")
        if ablate_map_history and (map_encoder != "none" or map_latents or coordinate_embedding != "linear"):
            raise ValueError("map/history ablation requires plain map tokens (no encoder, latents or PLR)")
        self.model_config = {k: v for k, v in locals().items() if k not in {"self", "__class__"}}
        self.ablate_map_history = ablate_map_history
        if global_conditioning not in {"token", "late_concat"}:
            raise ValueError("Invalid global conditioning")
        if map_encoder not in {"none", "edge", "node_mlp", "dag"}:
            raise ValueError("Invalid map encoder")
        if map_latents not in {0, 16, 32} or register_tokens not in {0, 2, 4}:
            raise ValueError("Invalid latent/register count")
        if coordinate_embedding not in {"linear", "plr"} or pooling not in {"cls", "cls_mean"}:
            raise ValueError("Invalid coordinate embedding/pooling")
        if not use_map_context and (map_encoder != "none" or map_latents or coordinate_embedding != "linear"):
            raise ValueError("Map experiments require map context")
        budget = item_token_budget if item_token_budget is not None else (73 if use_map_context else 192)
        if isinstance(budget, bool) or not isinstance(budget, int) or budget <= 0:
            raise ValueError("item_token_budget must be a positive integer")
        if use_map_context and budget != 73:
            raise ValueError("V4 map models retain the 73-item contract")
        self.model_config["item_token_budget"] = budget
        self.item_token_budget = budget
        self.global_conditioning, self.norm_position = global_conditioning, norm_position
        self.use_map_context, self.pooling = use_map_context, pooling
        self.map_encoder, self.coordinate_embedding = map_encoder, coordinate_embedding
        self.token_emb = nn.Embedding(vocab_size, d_model, padding_idx=0)
        self.upgrade_emb = nn.Embedding(max_upgrade, d_model, padding_idx=0)
        self.count_emb = nn.Embedding(max_count, d_model, padding_idx=0)
        self.cls_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.registers = nn.Parameter(torch.randn(1, register_tokens, d_model) * 0.02) if register_tokens else None
        self.character_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.character_emb = nn.Embedding(num_characters, d_model)
        self.boss_token = nn.Parameter(torch.randn(1, 1, d_model))
        self.boss_emb = nn.Embedding(num_bosses, d_model)
        self.global_mlp = nn.Sequential(nn.Linear(num_global_features, d_model), nn.ReLU(), nn.Linear(d_model, d_model))
        if use_map_context:
            self.token_type_emb = nn.Embedding(self.NUM_CONTEXT_TYPES, d_model)
            self.map_room_emb = nn.Embedding(self.NUM_ITEM_ROOMS, d_model, padding_idx=0)
            self.map_flag_proj = nn.Linear(7 if coordinate_embedding == "linear" else 5, d_model)
            if coordinate_embedding == "plr":
                self.coordinate_plr = CoordinatePLR(d_model)
            if map_encoder != "none":
                self.map_edge_proj = nn.Linear(3, d_model, bias=False)
            if map_encoder in {"node_mlp", "dag"}:
                self.map_node_encoder = MapNodeEncoder(d_model, map_encoder, norm_type)
            if map_latents:
                self.map_pool = MapLatentPool(d_model, n_heads, map_latents, dropout, norm_type)
            self.hist_dist_emb = nn.Embedding(HISTORY_HORIZON + 1, d_model, padding_idx=self.HISTORY_DIST_PAD)
            self.keys_mlp = nn.Sequential(nn.Linear(num_key_features, d_model), nn.ReLU(), nn.Linear(d_model, d_model))
            xy = torch.stack((torch.linspace(0, 1, 7).repeat(15),
                              torch.linspace(0, 1, 15).repeat_interleave(7)), dim=1)
            self.register_buffer("map_cell_coordinates", xy, persistent=False)
        self.layers = nn.ModuleList([
            SetAttention(d_model, n_heads, dropout, norm_position, ffn_type, norm_type)
            for _ in range(n_layers)
        ])
        self.final_norm = make_norm(d_model, norm_type) if norm_position == "pre" else nn.Identity()
        head_input = d_model * (1 + int(pooling == "cls_mean") + int(global_conditioning == "late_concat"))
        self.value_head = nn.Sequential(nn.Linear(head_input, d_model // 2), nn.ReLU(), nn.Linear(d_model // 2, VALUE_OUTPUT_DIM))
        if use_map_context:
            self.key_head = nn.Linear(head_input, 3)

    def _decode_map_flags(self, map_flags):
        xy = self.map_cell_coordinates.to(device=map_flags.device, dtype=torch.float32)
        columns = [((map_flags >> bit) & 1).float().unsqueeze(-1) for bit in (1, 2, 3)]
        degrees = [((map_flags >> bit) & 3).float().div(3).unsqueeze(-1) for bit in (4, 6)]
        return torch.cat(columns + [xy.unsqueeze(0).expand(map_flags.size(0), -1, -1)] + degrees, dim=-1)

    def _context_tokens(self, keys_features, map_rooms, map_flags, history_rooms,
                        history_dist, batch_size, device, map_outgoing_mask=None):
        if keys_features is None:
            keys_features = torch.zeros(batch_size, self.keys_mlp[0].in_features, device=device)
        if map_rooms is None:
            map_rooms = torch.zeros(batch_size, MAP_GRID_CELLS, dtype=torch.long, device=device)
        if map_flags is None:
            map_flags = torch.zeros_like(map_rooms)
        if history_rooms is None:
            history_rooms = torch.zeros(batch_size, HISTORY_HORIZON, dtype=torch.long, device=device)
        if history_dist is None:
            history_dist = torch.full_like(history_rooms, self.HISTORY_DIST_PAD)
        map_rooms, map_flags, history_rooms, history_dist = [
            t.to(device=device, dtype=torch.long) for t in (map_rooms, map_flags, history_rooms, history_dist)
        ]
        if self.ablate_map_history:
            # Retain all fixed slots and mask them; leave the key channel intact.
            map_rooms = torch.zeros_like(map_rooms)
            map_flags = torch.zeros_like(map_flags)
            if map_outgoing_mask is not None:
                map_outgoing_mask = torch.zeros_like(map_outgoing_mask)
        if map_outgoing_mask is None:
            if self.map_encoder != "none" and map_rooms.ne(0).any():
                raise ValueError("Map encoder requires map_outgoing_mask for a present map")
            map_outgoing_mask = torch.zeros_like(map_flags)
        map_outgoing_mask = map_outgoing_mask.to(device=device, dtype=torch.long)
        keys_out = self.keys_mlp(keys_features.to(device)).unsqueeze(1) + self.token_type_emb.weight[4]
        features = self._decode_map_flags(map_flags)
        if self.coordinate_embedding == "linear":
            projected = self.map_flag_proj(features)
        else:
            projected = self.map_flag_proj(torch.cat([features[..., :3], features[..., 5:]], -1))
            projected = projected + self.coordinate_plr(features[..., 3:5])
        map_out = self.map_room_emb(map_rooms) + projected + self.token_type_emb.weight[5]
        map_mask = map_rooms.eq(0)
        if self.map_encoder != "none":
            bits = torch.stack([((map_outgoing_mask >> b) & 1).float() for b in range(3)], dim=-1)
            # Independent-node control has identical parameters but no edge signal.
            if self.map_encoder == "node_mlp":
                bits = torch.zeros_like(bits)
            map_out = map_out + self.map_edge_proj(bits)
        if self.map_encoder in {"node_mlp", "dag"}:
            map_out = self.map_node_encoder(map_out, map_outgoing_mask, map_mask)
        if hasattr(self, "map_pool"):
            map_out, map_mask = self.map_pool(map_out, map_mask)
        hist_out = self.map_room_emb(history_rooms) + self.hist_dist_emb(history_dist) + self.token_type_emb.weight[6]
        history_mask = (torch.ones_like(history_dist, dtype=torch.bool)
                        if self.ablate_map_history else history_dist.eq(self.HISTORY_DIST_PAD))
        return (keys_out, map_out, hist_out, torch.zeros(batch_size, 1, dtype=torch.bool, device=device),
                map_mask, history_mask)

    def forward(self, seq_tokens, upgrades, counts, global_features, character_ids,
                boss_ids, keys_features=None, map_rooms=None, map_flags=None,
                history_rooms=None, history_dist=None, return_keys=False,
                map_outgoing_mask=None):
        batch, device = seq_tokens.size(0), seq_tokens.device
        items = self.token_emb(seq_tokens) + self.upgrade_emb(upgrades) + self.count_emb(counts)
        item_mask = seq_tokens.eq(0)
        empty = torch.zeros(batch, 1, dtype=torch.bool, device=device)
        cls = self.cls_token.expand(batch, -1, -1)
        character = self.character_token.expand(batch, -1, -1) + self.character_emb(character_ids).unsqueeze(1)
        boss = self.boss_token.expand(batch, -1, -1) + self.boss_emb(boss_ids).unsqueeze(1)
        global_out = self.global_mlp(global_features)
        if self.use_map_context:
            cls = cls + self.token_type_emb.weight[0]
            character = character + self.token_type_emb.weight[2]
            boss = boss + self.token_type_emb.weight[3]
            items = items + self.token_type_emb.weight[7]
        blocks, masks = [cls], [empty]
        if self.registers is not None:
            blocks.append(self.registers.expand(batch, -1, -1))
            masks.append(torch.zeros(batch, self.registers.size(1), dtype=torch.bool, device=device))
        if self.global_conditioning == "token":
            global_token = global_out.unsqueeze(1)
            if self.use_map_context:
                global_token = global_token + self.token_type_emb.weight[1]
            blocks.append(global_token)
            masks.append(empty)
        blocks.extend([character, boss])
        masks.extend([empty, empty])
        if self.use_map_context:
            k, m, h, km, mm, hm = self._context_tokens(
                keys_features, map_rooms, map_flags, history_rooms, history_dist,
                batch, device, map_outgoing_mask,
            )
            blocks.extend([k, m, h])
            masks.extend([km, mm, hm])
        item_start = sum(block.size(1) for block in blocks)
        x = torch.cat(blocks + [items], dim=1)
        padding = torch.cat(masks + [item_mask], dim=1)
        for layer in self.layers:
            x = layer(x, key_padding_mask=padding)
        x = self.final_norm(x)
        pooled = [x[:, 0]]
        if self.pooling == "cls_mean":
            valid = (~item_mask).unsqueeze(-1)
            pooled.append((x[:, item_start:] * valid).sum(1) / valid.sum(1).clamp_min(1))
        if self.global_conditioning == "late_concat":
            pooled.append(global_out)
        pooled = torch.cat(pooled, dim=-1)
        logits = ValueOutputs.from_flat(self.value_head(pooled))
        if return_keys:
            if not self.use_map_context:
                raise ValueError("key head requires use_map_context=True")
            return logits, self.key_head(pooled)
        return logits
