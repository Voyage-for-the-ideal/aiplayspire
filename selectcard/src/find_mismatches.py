import glob
import json
import gzip
import os
import collections
from reconstructor import RunReconstructor

def list_mismatches(limit=500, output_file="mismatched_runs.txt"):
    # 查找数据文件
    data_dir = r"D:\code\masterspire\selectcard\STS Data"
    files = glob.glob(os.path.join(data_dir, "**", "*.json.gz"), recursive=True)
    if not files:
        files = glob.glob(r"D:\code\masterspire\selectcard\**\*.json", recursive=True)
        
    mismatches = []
    total_valid = 0

    print(f"Scanning {len(files)} files...")

    for f in files:
        if len(mismatches) >= 20: # 找到20个就够了
            break
            
        try:
            if f.endswith('.gz'):
                with gzip.open(f, 'rt', encoding='utf-8') as gz:
                    d = json.load(gz)
            else:
                with open(f, 'r', encoding='utf-8') as js:
                    d = json.load(js)
        except Exception:
            continue

        runs = d if isinstance(d, list) else [d]
        for run in runs:
            ev = run.get('event', run)
            recon = RunReconstructor(ev)
            
            if not recon.validate_run():
                continue

            total_valid += 1
            # 执行重放以构建最终卡组
            for _ in recon.replay():
                pass

            if not recon.is_match_with_master_deck():
                # 收集差异
                sim_cnt = collections.Counter(recon.deck)
                master_cnt = collections.Counter(recon.master_deck)
                
                excess = list((sim_cnt - master_cnt).elements())
                missing = list((master_cnt - sim_cnt).elements())
                
                mismatches.append({
                    'file': f,
                    'seed': ev.get('seed_played'),
                    'character': recon.character,
                    'floor_reached': recon.floor_reached,
                    'excess': excess,
                    'missing': missing,
                    'neow_bonus': ev.get('neow_bonus'),
                    'relics': ev.get('relics', [])
                })
                
                if len(mismatches) >= 20:
                    break

    # 输出到文件
    with open(output_file, "w", encoding="utf-8") as out:
        out.write(f"Total Valid Runs Checked: {total_valid}\n")
        out.write(f"Mismatched Runs Found: {len(mismatches)}\n")
        out.write("="*50 + "\n\n")
        
        for i, m in enumerate(mismatches):
            out.write(f"Example {i+1}:\n")
            out.write(f"File: {m['file']}\n")
            out.write(f"Seed: {m['seed']}\n")
            out.write(f"Character: {m['character']} | Floor: {m['floor_reached']}\n")
            out.write(f"Neow Bonus: {m['neow_bonus']}\n")
            out.write(f"Relics: {', '.join(m['relics'][:10])}...\n")
            out.write(f"Excess in simulated deck (should not be there): {m['excess']}\n")
            out.write(f"Missing in simulated deck (should be there): {m['missing']}\n")
            out.write("-" * 30 + "\n")

    print(f"Done. Mismatches saved to {output_file}")

if __name__ == '__main__':
    list_mismatches()
