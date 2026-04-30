import os
import json
import glob
import gzip
import pandas as pd
from datetime import datetime
from reconstructor import RunReconstructor

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
        if not recon.validate_run():
            continue
            
        floor_reached = recon.floor_reached
        for snapshot in recon.replay():
            floor = snapshot['floor']
            
            # 判断 Label (当前 Act 存活)
            if floor <= 16:
                label = 1 if floor_reached > 16 else 0
            elif floor <= 33:
                label = 1 if floor_reached > 33 else 0
            else:
                label = 1 if floor_reached >= 50 else 0
            # 应用隐性替换操作
            if floor > 0:
                for c in recon._implicit_removals.get(floor, []):
                    recon._remove_card(c)
                for c in recon._implicit_additions.get(floor, []):
                    recon.deck.append(c)

            snapshot['label'] = label
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
    OUTPUT_DIR = os.path.join(BASE_DIR, "processed_data")
    build_dataset(DATA_DIR, OUTPUT_DIR)

