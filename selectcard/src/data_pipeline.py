import os
import json
import glob
import gzip
import pandas as pd
import hashlib
from datetime import datetime
from reconstructor import RunReconstructor

try:
    from encoding import PREPROCESSING_VERSION
    from config import Config
except ImportError:
    from .encoding import PREPROCESSING_VERSION
    from .config import Config


def stable_run_id(event_data):
    payload = json.dumps(event_data, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def assign_split(run_id):
    split_total = Config.TRAIN_SPLIT + Config.VAL_SPLIT + Config.TEST_SPLIT
    if abs(split_total - 1.0) > 1e-9:
        raise ValueError("TRAIN_SPLIT + VAL_SPLIT + TEST_SPLIT must equal 1")
    split_key = f"{Config.SPLIT_SEED}:{run_id}".encode("ascii")
    bucket = int(hashlib.sha256(split_key).hexdigest()[:8], 16) % 10000
    train_end = int(Config.TRAIN_SPLIT * 10000)
    val_end = train_end + int(Config.VAL_SPLIT * 10000)
    if bucket < train_end:
        return "train"
    if bucket < val_end:
        return "val"
    return "test"


def is_reconstructable_run(recon):
    """Apply structural filters while retaining manually abandoned runs as censored."""
    if recon.ascension < 15 or recon.floor_reached <= 0:
        return False
    if "PrismaticShard" in recon.raw_data.get("relics", []):
        return False
    if recon.character not in {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}:
        return False
    if not recon.raw_data.get("character_chosen"):
        return False
    deck_size = len(recon.deck)
    master_size = len(recon.master_deck) if recon.master_deck else 0
    if master_size and abs(deck_size - master_size) > 10:
        return False
    if master_size and deck_size != master_size and recon._has_shop_visit():
        return False
    return True


def act_target(floor, floor_reached, terminal_known):
    boundary = 17 if floor <= 16 else 34 if floor <= 33 else 50
    if floor_reached >= boundary:
        return 1, True
    return 0, terminal_known

def process_file(filepath):
    # 处理单个 JSON / JSON.gz 文件
    try:
        if filepath.endswith('.gz'):
            with gzip.open(filepath, 'rt', encoding='utf-8') as f:
                data = json.load(f)
        else:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
        return []
        
    runs = data if isinstance(data, list) else [data]
    
    samples = []
    threshold_date = datetime(2020, 1, 14)
    for run in runs:
        event_data = run.get('event', run) 
        
        # 过滤 2020.1.14 之前的对局
        local_time = event_data.get('local_time')
        if local_time:
            try:
                # 示例格式: "20200930012914" (YYYYMMDDHHMMSS)
                run_date = datetime.strptime(local_time[:8], '%Y%m%d')
                if run_date < threshold_date:
                    continue
            except Exception:
                pass # 如果解析失败，默认保留或跳过？这里选择保留或者根据需求处理

        recon = RunReconstructor(event_data)
        if not is_reconstructable_run(recon):
            continue

        run_id = stable_run_id(event_data)
        split = assign_split(run_id)
        terminal_known = bool(recon.is_victory or recon.killed_by)
        floor_reached = recon.floor_reached
        for snapshot in recon.replay():
            floor = snapshot['floor']
            label, target_valid = act_target(floor, floor_reached, terminal_known)

            snapshot['label'] = label
            snapshot['target_valid'] = target_valid
            snapshot['run_id'] = run_id
            snapshot['split'] = split
            snapshot['preprocessing_version'] = PREPROCESSING_VERSION
            # Convert list of strings to string representation for parquet compatibility easily
            snapshot['deck'] = ",".join(snapshot['deck'])
            snapshot['relics'] = ",".join(snapshot['relics'])
            snapshot['candidates'] = ",".join(snapshot['candidates'])
            
            samples.append(snapshot)
            
    return samples

def build_dataset(data_dir, output_dir, chunk_size=50000):
    os.makedirs(output_dir, exist_ok=True)
    all_files = glob.glob(os.path.join(data_dir, "**/*.json"), recursive=True)
    all_files += glob.glob(os.path.join(data_dir, "**/*.json.gz"), recursive=True)
    total_files = len(all_files)
    print(f"Found {total_files} files in {data_dir}.")
    
    if total_files == 0:
        return

    chunk_data = []
    chunk_index = 0
    total_samples = 0
    
    import concurrent.futures
    import sys
    
    # 使用多进程加速并行处理
    with concurrent.futures.ProcessPoolExecutor() as executor:
        futures = {executor.submit(process_file, f): f for f in all_files}
        
        completed = 0
        for future in concurrent.futures.as_completed(futures):
            completed += 1
            # 每处理 500 个文件或者结束时输出一次进度，避免刷屏
            if completed % 500 == 0 or completed == total_files:
                sys.stdout.write(f"\rProcessing files: {completed}/{total_files} ({(completed/total_files)*100:.1f}%)")
                sys.stdout.flush()
            
            try:
                samples = future.result()
                if samples:
                    chunk_data.extend(samples)
            except Exception as e:
                pass
                # print(f"\nError processing a file: {e}")
                
            # 当累积样本数达到 chunk_size 时再写入
            if len(chunk_data) >= chunk_size:
                df = pd.DataFrame(chunk_data)
                out_path = os.path.join(output_dir, f"data_chunk_{chunk_index:04d}.parquet")
                df.to_parquet(out_path, index=False)
                
                total_samples += len(chunk_data)
                chunk_data = []
                chunk_index += 1
                
    print() # 进度条结束后换行
    
    # 保存最后剩余不足一个区块的数据
    if chunk_data:
        df = pd.DataFrame(chunk_data)
        out_path = os.path.join(output_dir, f"data_chunk_{chunk_index:04d}.parquet")
        df.to_parquet(out_path, index=False)
        total_samples += len(chunk_data)
        chunk_index += 1
        
    print(f"Extraction complete. Total choice samples: {total_samples}, saved in {chunk_index} chunks.")

if __name__ == "__main__":
    # 获取当前脚本所在目录(src)的上一级目录(selectcard)，这样能自适应本地和Colab环境
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    DATA_DIR = os.path.join(BASE_DIR, "STS Data")
    OUTPUT_DIR = os.path.join(BASE_DIR, "processed_data_v2")
    build_dataset(DATA_DIR, OUTPUT_DIR)

