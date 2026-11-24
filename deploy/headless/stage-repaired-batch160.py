"""Provision fresh A10 lanes only after 102 signed triggers and all regressions pass."""
import argparse
import json
from pathlib import Path
import shutil

from sts_ai_framework.headless.repair_ledger import digest, inventory
from sts_ai_framework.headless.repair_signoff import require, runtime, release_digest
from sts_ai_framework.headless.agent import save_json

P=Path('/home/ubuntu/headless-spire-poc')
OLD=P/'runtime-acceptance-batch160-v1'
FIX=P/'runtime-acceptance-batch160-repairs-v2'
NEW=P/'runtime-acceptance-batch160-v2'
PREVIOUS=P/'runtime-acceptance-batch160-repairs-v1/reports/repair-audit.json'
CONTENTS=('repo','tools','classes','native-bin','profile-baseline','profile-baseline-manifest.json','value-format12.pth')
RUNNERS=('run-batch160.sh','audit-batch160.py','stage-repaired-batch160.py')


def check_proof(path, root):
    proof=json.loads(path.read_bytes())
    require(proof.get('schema')=='headless-102-signoff-v1' and proof['passed'] is True and proof['verified']==102
            and proof['original_report_sha256']==digest(OLD/'reports/batch160-report.json') and proof['training'] is False,
            'No complete signoff for the original 160-run batch')
    original={c['seed']:(c,d) for c,d in inventory(OLD,PREVIOUS)}
    require(len(original)==len(proof['cases'])==102 and {r['seed'] for r in proof['cases']}==set(original),'Not all 102 cases')
    for r in proof['cases']:
        c,d=original[r['seed']]
        require(r['passed'] is True and all(c[k]==r[k] for k in ('case_id','role','attempt_id','diagnostic_sha256')),'Case binding differs')
        require(r['status']=='recorded_step_matches' if 'after' in d else r['live']['independent_replay'] is True,'Target lacks proof')
    require(release_digest(root)==proof['release_sha256'] and runtime(root)==proof['runtime_sha256'] and digest(root/'value-format12.pth')==proof['checkpoint']
            and digest(root/'profile-baseline-manifest.json')==proof['profile'],'Candidate hash mismatch')
    return proof


def stage(proof_path):
    require(proof_path.resolve(strict=True).parent==FIX/'reports','Signoff must be in the isolated repair reports')
    check_proof(proof_path,FIX)
    NEW.mkdir(exist_ok=False);(NEW/'reports').mkdir();(NEW/'tools').mkdir()
    shutil.copy2(proof_path,NEW/'reports/repair-signoff.json')
    shutil.copy2(OLD/'preservation-baseline.json',NEW/'preservation-baseline.json')
    for name in RUNNERS:shutil.copy2(FIX/'tools'/name,NEW/'tools'/name)
    for i in range(2):
        lane=NEW/f'lane{i}';lane.mkdir()
        for name in CONTENTS:
            src=FIX/name;dst=lane/name
            if src.is_dir():shutil.copytree(src,dst,ignore=shutil.ignore_patterns('__pycache__','*.pyc','.git','debug'))
            else:shutil.copy2(src,dst)
        (lane/'reports').mkdir();(lane/'saves').mkdir()
        check_proof(proof_path,lane)
    files={}
    for p in NEW.rglob('*'):
        if p.is_file() and 'reports' not in p.relative_to(NEW).parts:files[str(p.relative_to(NEW))]=digest(p)
    files['reports/repair-signoff.json']=digest(proof_path)
    save_json(NEW/'frozen-runtime-manifest.json',dict(files=files))
    verify(NEW)


def verify(path):
    require(path.resolve(strict=True)==NEW and not NEW.is_symlink(),'Unexpected rerun root')
    for i in range(2):check_proof(NEW/'reports/repair-signoff.json',NEW/f'lane{i}')
    require(digest(NEW/'preservation-baseline.json')==digest(OLD/'preservation-baseline.json'),'Preservation baseline changed')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);g=parser.add_mutually_exclusive_group(required=True)
    g.add_argument('--stage',type=Path);g.add_argument('--verify',type=Path);a=parser.parse_args()
    if a.stage:stage(a.stage)
    else:verify(a.verify)

