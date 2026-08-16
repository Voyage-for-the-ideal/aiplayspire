import pandas as pd
import glob
import os
from config import Config
from data_contract import MASK_COLUMNS, TARGET_COLUMNS

def count_labels():
    data_dir = Config.DATA_DIR
    files = sorted(glob.glob(os.path.join(data_dir, "*.parquet")))
    
    if not files:
        print(f"在 {data_dir} 未找到 parquet 文件。")
        return

    totals = {target: {"positive": 0, "negative": 0} for target in TARGET_COLUMNS}

    print(f"开始统计目录: {data_dir}")
    print(f"找到 {len(files)} 个数据块...")

    for f in files:
        try:
            df = pd.read_parquet(f, columns=[*TARGET_COLUMNS, *MASK_COLUMNS])
            summary = []
            for target, mask in zip(TARGET_COLUMNS, MASK_COLUMNS):
                valid = df.loc[df[mask].astype(bool), target]
                positive = int((valid == 1).sum())
                negative = int((valid == 0).sum())
                totals[target]["positive"] += positive
                totals[target]["negative"] += negative
                summary.append(f"{target.removeprefix('target_')}={positive}/{negative}")
            print(f"  Processed {os.path.basename(f)}: " + ", ".join(summary))
        except Exception as e:
            print(f"  Error reading {f}: {e}")

    print("\n" + "="*30)
    print("统计结果汇报")
    print("="*30)
    for target in TARGET_COLUMNS:
        positive = totals[target]["positive"]
        negative = totals[target]["negative"]
        valid = positive + negative
        print(
            f"{target.removeprefix('target_')}: valid={valid}, positive={positive}, "
            f"negative={negative}, positive_rate={positive/max(1, valid)*100:.2f}%"
        )
    
    print("="*30)

if __name__ == "__main__":
    count_labels()
