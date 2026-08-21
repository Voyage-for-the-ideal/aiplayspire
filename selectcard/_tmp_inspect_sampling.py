"""临时盘点脚本：抽样统计 STS Data 中 run 的字段分布（只读，不生成数据）。"""
import gzip
import json
import glob
import os
import random
import collections

DATA = r"D:/code/aiplayspire/selectcard/STS Data"
random.seed(42)

files = sorted(
    glob.glob(os.path.join(DATA, "**", "*.json.gz"), recursive=True)
    + glob.glob(os.path.join(DATA, "**", "*.json"), recursive=True)
)
print("total files:", len(files))

# 每个子目录抽样最多 300 个文件（按名字排序后均匀取），控制总时长
sample = []
for d in sorted(set(os.path.dirname(f) for f in files)):
    fs = sorted(f for f in files if os.path.dirname(f) == d)
    sample.extend(fs[:300])
random.shuffle(sample)
print("sampled files:", len(sample))

parse_fail = 0
n_runs = 0
asc = collections.Counter()
chars = collections.Counter()
endings = collections.Counter()
no_master_deck = 0
with_event_key = 0
runs_per_file = collections.Counter()
total_choices = 0
no_choices = 0

for fp in sample:
    try:
        if fp.endswith(".gz"):
            with gzip.open(fp, "rt", encoding="utf-8") as f:
                data = json.load(f)
        else:
            with open(fp, "r", encoding="utf-8") as f:
                data = json.load(f)
    except Exception as e:
        parse_fail += 1
        continue
    runs = data if isinstance(data, list) else [data]
    runs_per_file[len(runs)] += 1
    for r in runs:
        n_runs += 1
        ev = r.get("event", r) if isinstance(r, dict) else r
        if "event" in r:
            with_event_key += 1
        if not isinstance(ev, dict):
            continue
        asc[ev.get("ascension_level", "MISSING")] += 1
        chars[ev.get("character_chosen", "MISSING")] += 1
        v = ev.get("victory", False)
        k = ev.get("killed_by", None)
        if v:
            endings["victory"] += 1
        elif k:
            endings["killed_by"] += 1
        else:
            endings["abandoned"] += 1
        if not ev.get("master_deck"):
            no_master_deck += 1
        choices = ev.get("card_choices", [])
        total_choices += len(choices)
        if not choices:
            no_choices += 1

print("parse_fail:", parse_fail)
print("runs:", n_runs)
print("runs_per_file:", dict(runs_per_file.most_common(5)))
print("has event key:", with_event_key)
print("ascension:", sorted(asc.items(), key=lambda x: -x[1])[:30])
print("characters:", chars.most_common())
print("endings:", endings.most_common())
print("no master_deck:", no_master_deck)
print("runs with 0 card_choices:", no_choices)
print("avg card_choices per run:", total_choices / max(n_runs, 1))
