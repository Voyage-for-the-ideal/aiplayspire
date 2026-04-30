import pandas as pd
import glob
import os
from config import Config

def count_labels():
    data_dir = Config.DATA_DIR
    files = sorted(glob.glob(os.path.join(data_dir, "*.parquet")))
    
    if not files:
        print(f"在 {data_dir} 未找到 parquet 文件。")
        return

    total_pos = 0
    total_neg = 0
    total_samples = 0

    print(f"开始统计目录: {data_dir}")
    print(f"找到 {len(files)} 个数据块...")

    for f in files:
        try:
            # 仅读取 label 列以节省内存和速度
            df = pd.read_parquet(f, columns=["label"])
            counts = df["label"].value_counts()
            
            pos = counts.get(1.0, 0) + counts.get(1, 0)
            neg = counts.get(0.0, 0) + counts.get(0, 0)
            
            total_pos += pos
            total_neg += neg
            total_samples += len(df)
            
            print(f"  Processed {os.path.basename(f)}: Pos={pos}, Neg={neg}")
        except Exception as e:
            print(f"  Error reading {f}: {e}")

    print("\n" + "="*30)
    print("统计结果汇报")
    print("="*30)
    print(f"总样本数: {total_samples}")
    print(f"正样本 (1): {total_pos} ({total_pos/max(1, total_samples)*100:.2f}%)")
    print(f"负样本 (0): {total_neg} ({total_neg/max(1, total_samples)*100:.2f}%)")
    
    if total_neg > 0:
        ratio = total_pos / total_neg
        print(f"正负比例 (1:0): {ratio:.2f} : 1")
    else:
        print("未发现负样本 (0)")
    
    print("="*30)

if __name__ == "__main__":
    count_labels()
