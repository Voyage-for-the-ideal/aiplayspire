import torch
import torch.nn as nn
import math

class SetAttention(nn.Module):
    def __init__(self, d_model, n_heads, dropout=0.1):
        super().__init__()
        self.mha = nn.MultiheadAttention(d_model, n_heads, dropout=dropout, batch_first=True)
        self.norm1 = nn.LayerNorm(d_model)
        self.norm2 = nn.LayerNorm(d_model)
        self.ffn = nn.Sequential(
            nn.Linear(d_model, d_model * 4),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(d_model * 4, d_model),
            nn.Dropout(dropout)
        )
        
    def forward(self, x, key_padding_mask=None):#forward函数实现了Setattention前向传播模块
        # x shape: [Batch, SeqLen, d_model]
        # Set attention is permutation invariant (no positional encoding)
        attn_out, _ = self.mha(x, x, x, key_padding_mask=key_padding_mask)
        x = self.norm1(x + attn_out)
        ffn_out = self.ffn(x)
        x = self.norm2(x + ffn_out)
        return x

class STSValueNetwork(nn.Module):
    """
    Survival-Value Network (存活期望评估网络)
    针对项目方案：不评估单卡，只评估全量状态
    """
    def __init__(self, vocab_size, max_upgrade=15, max_count=10, d_model=128, n_heads=4, n_layers=3, num_global_features=8, dropout=0.1):
        super().__init__()
        # 基础物品 ID Embedding (Card / Relic)
        self.token_emb = nn.Embedding(vocab_size, d_model)
        
        # 升级修饰词 Embedding (Upgrade Encoding) - 第0维表示未升级
        self.upgrade_emb = nn.Embedding(max_upgrade, d_model)
        
        # 数量修饰词 Embedding (Count Encoding) - 第0维保留或用1表示1张
        self.count_emb = nn.Embedding(max_count, d_model)
        
        # 为了区别分类还是特征，可加一个 [CLS] token
        self.cls_token = nn.Parameter(torch.randn(1, 1, d_model))
        
        # Transformer 处理无序的多物品（卡组+遗物）
        self.layers = nn.ModuleList([SetAttention(d_model, n_heads, dropout=dropout) for _ in range(n_layers)])
        
        # 全局连续特征 (血量，金币，层数，进阶等)
        self.global_mlp = nn.Sequential(
            nn.Linear(num_global_features, d_model),
            nn.ReLU(),
            nn.Linear(d_model, d_model)
        )
        
        # 最后的 Value 预测头
        self.value_head = nn.Sequential(
            nn.Linear(d_model * 2, int(d_model / 2)),
            nn.ReLU(),
            nn.Linear(int(d_model / 2), 1)
        )
        
    def forward(self, seq_tokens, upgrades, counts, global_features):#forward函数实现了STSValueNetwork前向传播模块
        """
        seq_tokens: [Batch, SeqLen] 物品 ID
        upgrades: [Batch, SeqLen] 升级等级 (0表示未升级, 1表示+1)
        counts: [Batch, SeqLen] 拥有数量 (0表示该位置padding, 或者1表示1张)
        global_features: [Batch, num_global_features] 当前层数/hp/gold等
        """
        B, seq_len = seq_tokens.size()
        
        # 1. 组合 Token 向量: Token = ID_Emb + Upgrade_Emb + Count_Emb
        x_tok = self.token_emb(seq_tokens)
        x_upg = self.upgrade_emb(upgrades)
        x_cnt = self.count_emb(counts)
        x = x_tok + x_upg + x_cnt
        
        # 2. 拼凑 CLS
        cls_tokens = self.cls_token.expand(B, -1, -1)
        x = torch.cat([cls_tokens, x], dim=1) # [Batch, SeqLen+1, d_model]

        # Padding mask: True 表示被忽略。CLS 位置永远不 mask。
        padding_mask = (seq_tokens == 0)
        cls_pad = torch.zeros((B, 1), dtype=torch.bool, device=seq_tokens.device)
        key_padding_mask = torch.cat([cls_pad, padding_mask], dim=1)
        
        # 3. 走 Set Transformer
        for layer in self.layers:
            x = layer(x, key_padding_mask=key_padding_mask)
            
        # 提取 CLS 结果
        cls_out = x[:, 0, :] # [Batch, d_model]
        
        # 4. 全局特征 MLP
        global_out = self.global_mlp(global_features) # [Batch, d_model]
        
        # 5. 拼接输出 Logits
        merged = torch.cat([cls_out, global_out], dim=-1)
        value = self.value_head(merged)
        
        return value
