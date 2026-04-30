import torch
from torch.utils.data import Dataset
import pandas as pd
import glob
import os
import re
from collections import Counter
import bisect
from functools import lru_cache

class SimpleTokenizer:
    """简单的 Tokenizer：将字符串态的卡牌/遗物转化为 ID"""
    def __init__(self):
        self.item2id = {"[PAD]": 0, "[MASK]": 1}
        self.id2item = {0: "[PAD]", 1: "[MASK]"}
        
    def get_id(self, item_name):
        if item_name not in self.item2id:
            new_id = len(self.item2id)
            self.item2id[item_name] = new_id
            self.id2item[new_id] = item_name
        return self.item2id[item_name]
        
    def __len__(self):
        return len(self.item2id)

    def save(self, filepath):
        import json
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.item2id, f, ensure_ascii=False, indent=2)
            
    def load(self, filepath):
        import json
        with open(filepath, 'r', encoding='utf-8') as f:
            self.item2id = json.load(f)
            self.id2item = {v: k for k, v in self.item2id.items()}

class STSDataset(Dataset):
    def __init__(self, parquet_dir, tokenizer, max_seq_len=64):
        """
        :param parquet_dir: 存放 data_chunk_xxx.parquet 的目录
        """
        self.tokenizer = tokenizer
        self.max_seq_len = max_seq_len
        self.files = sorted(glob.glob(os.path.join(parquet_dir, "*.parquet")))
        
        # 为了避免 OOM，不一次性读入所有数据，而是预先统计每个 chunk 的长度
        self.file_lengths = []
        self.cumulative_lengths = []
        total = 0
        for f in self.files:
            # 仅读取一列以便极快地获取行数，避免读取整个庞大 DataFrame
            try:
                num_rows = len(pd.read_parquet(f, columns=["floor"]))
            except Exception:
                # 兼容性备用读取方案
                num_rows = len(pd.read_parquet(f))
            self.file_lengths.append(num_rows)
            total += num_rows
            self.cumulative_lengths.append(total)
            
        self.total_samples = total
        print(f"Registered {len(self.files)} Parquet chunks, total {self.total_samples} samples.")

        # 全局特征处理策略：层数/难度做归一化，血量/金币做标准化
        self.floor_norm_scale = 55.0
        self.ascension_norm_scale = 20.0
        self.std_feature_names = ["hp", "gold"]
        self.std_feature_defaults = [0.0, 0.0]
        self.std_means, self.std_stds = self._compute_standardize_stats()

    def __len__(self):
        return self.total_samples

    @lru_cache(maxsize=16) # 根据内存限制自动缓存近期最常访问的 chunk 避免重复 I/O
    def _get_df(self, file_idx):
        return pd.read_parquet(self.files[file_idx])

    def _compute_standardize_stats(self):
        sums = torch.zeros(len(self.std_feature_names), dtype=torch.float64)
        sq_sums = torch.zeros(len(self.std_feature_names), dtype=torch.float64)
        total_count = 0

        for f in self.files:
            try:
                df = pd.read_parquet(f, columns=self.std_feature_names)
            except Exception:
                df = pd.read_parquet(f)

            values = []
            for name, default in zip(self.std_feature_names, self.std_feature_defaults):
                if name in df.columns:
                    raw_series = df[name]
                else:
                    raw_series = pd.Series([default] * len(df))
                series = pd.to_numeric(raw_series, errors="coerce").fillna(default)
                values.append(torch.tensor(series.to_numpy(), dtype=torch.float64))

            if not values:
                continue

            stacked = torch.stack(values, dim=1)
            sums += stacked.sum(dim=0)
            sq_sums += (stacked ** 2).sum(dim=0)
            total_count += stacked.size(0)

        if total_count == 0:
            means = torch.tensor(self.std_feature_defaults, dtype=torch.float32)
            stds = torch.ones(len(self.std_feature_names), dtype=torch.float32)
            return means, stds

        means = sums / total_count
        vars_ = (sq_sums / total_count) - (means ** 2)
        vars_ = torch.clamp(vars_, min=1e-8)
        stds = torch.sqrt(vars_)

        return means.to(torch.float32), stds.to(torch.float32)

    def __getitem__(self, idx):
        # 二分查找以确定当前 idx 位于哪一个 Parquet chunk 中
        file_idx = bisect.bisect_right(self.cumulative_lengths, idx)
        
        # 计算在当前 chunk 内的局部索引
        if file_idx == 0:
            local_idx = idx
        else:
            local_idx = idx - self.cumulative_lengths[file_idx - 1]
            
        # 根据 LRU 缓存按需加载对应 Chunk
        df = self._get_df(file_idx)
        row = df.iloc[local_idx]
        
        # 1. 序列特征 (这里假设 DataFrame 里存的是 List 格式的字符串，或者用逗号分割的组合)
        # 您需要根据 data_pipeline.py 实际导出的列名 ('deck', 'relics') 做微调
        raw_deck = row.get("deck", [])
        if isinstance(raw_deck, str): 
            raw_deck = raw_deck.split(",") # 如果存成了逗号分隔字符串
            
        raw_relics = row.get("relics", [])
        if isinstance(raw_relics, str):
            raw_relics = raw_relics.split(",")
            
        # Tokenizer 处理去重、统计、转化为 ID
        # 实现方案中的 Count Encoding & Upgrade Encoding 分离逻辑
        seq_tokens = []
        upgrades = []
        counts = []
        
        # 统计数量
        item_counts = Counter([item for item in (raw_deck + raw_relics) if item])
        
        for item, count in item_counts.items():
            # 提取升级层数，如 "Strike_R+1" -> base_name="Strike_R", upgrade_level=1
            match = re.match(r'^(.*?)(?:\+(\d+))?$', item)
            if match:
                base_name = match.group(1)
                upgrade_level = int(match.group(2)) if match.group(2) else 0
            else:
                base_name = item
                upgrade_level = 0
                
            seq_tokens.append(self.tokenizer.get_id(base_name))
            upgrades.append(upgrade_level)
            counts.append(count)
            
        # Pad 到定长
        if len(seq_tokens) < self.max_seq_len:
            pad_len = self.max_seq_len - len(seq_tokens)
            seq_tokens += [0] * pad_len
            upgrades += [0] * pad_len
            counts += [0] * pad_len
        else:
            # 截断
            seq_tokens = seq_tokens[:self.max_seq_len]
            upgrades = upgrades[:self.max_seq_len]
            counts = counts[:self.max_seq_len]

        # 2. 全局特征 [层数归一化, 血量标准化, 金币标准化, 难度归一化]
        floor = float(row.get("floor", 1.0))
        hp = float(row.get("hp", 0.0))
        gold = float(row.get("gold", 0.0))
        ascension = float(row.get("ascension", 20.0))

        floor_norm = max(0.0, min(floor / self.floor_norm_scale, 1.0))
        ascension_norm = max(0.0, min(ascension / self.ascension_norm_scale, 1.0))
        hp_std = (hp - float(self.std_means[0])) / float(self.std_stds[0])
        gold_std = (gold - float(self.std_means[1])) / float(self.std_stds[1])

        global_feats = [floor_norm, hp_std, gold_std, ascension_norm]

        # 补齐 global features 长度到 8维（对应模型预设）
        global_feats += [0.0] * (8 - len(global_feats))
        
        # 3. Label: 能否存活过本章 (1.0 活下去了，0.0 暴毙了)
        label = float(row.get("label", 0.0))
        
        return (
            torch.tensor(seq_tokens, dtype=torch.long),
            torch.tensor(upgrades, dtype=torch.long),
            torch.tensor(counts, dtype=torch.long),
            torch.tensor(global_feats, dtype=torch.float32),
            torch.tensor([label], dtype=torch.float32)
        )
