"""Run both offline suites against a candidate and bind the logs to that exact build."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from sts_ai_framework.headless.repair_signoff import runtime, MIN_TESTS
from sts_ai_framework.headless.repair_ledger import digest
from sts_ai_framework.headless.agent import save_json

p=argparse.ArgumentParser()
p.add_argument('--root',required=True,type=Path);p.add_argument('--name',required=True)
a=p.parse_args();root=a.root.resolve(strict=True)
if not re.fullmatch('[a-z0-9-]+',a.name):raise ValueError('invalid audit name')
files=runtime(root);report=root/'reports'/('suites-'+a.name+'.json')
if report.exists():raise ValueError('Immutable report already exists')
suites={};env=dict(os.environ,PYTHONPATH=str(root/'repo'),PYTHONDONTWRITEBYTECODE='1',
                   BATTLE_ENGINE_WORKER=str(root/'native-bin/battle-engine-worker'),OMP_NUM_THREADS='1',
                   MKL_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
for name,start in [('framework','sts_ai_framework/tests'),('native','battle_engine/tests')]:
    log=root/'reports'/f'suite-{a.name}-{name}.log'
    with log.open('xb') as f:
        proc=subprocess.run([sys.executable,'-B','-m','unittest','discover','-s',start,'-t','.','-v'],
                            cwd=root/'repo',env=env,stdout=f,stderr=subprocess.STDOUT)
    text=log.read_text(errors='replace');counts=re.findall(r'^Ran (\d+) tests? in ',text,re.M)
    suites[name]=dict(log=str(log),sha256=digest(log),ran=int(counts[-1]) if counts else 0,
                      skips=len(re.findall(r" \.\.\. skipped ",text)),exit_code=proc.returncode)
if runtime(root)!=files: raise ValueError('Candidate changed while suites were running')
save_json(report,dict(runtime_sha256=files,suites=suites))
print(json.dumps(suites));sys.exit(int(not all(r['ran']>=MIN_TESTS[k] and r['skips']==0 and r['exit_code']==0 for k,r in suites.items())))
