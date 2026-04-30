import glob
import json
import gzip
from reconstructor import RunReconstructor

def test_reconstructor(limit=200):
    files = glob.glob(r"D:\code\masterspire\selectcard\STS Data\**\*.json.gz", recursive=True)
    if not files:
        files = glob.glob(r"D:\code\masterspire\selectcard\**\*.json", recursive=True)
        
    success = 0
    total = 0

    for f in files[:limit]:
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

            total += 1
            list(recon.replay())

            if recon.is_match_with_master_deck():
                success += 1
                
    if total > 0:
        print(f"\nStats: Valid Runs {total}, Perfect Match {success}, Accuracy {success/total*100:.2f}%")
    else:
        print("\nNo valid runs found in the parsed data.")

if __name__ == '__main__':
    test_reconstructor(100)