"""Final report for the finite five-confirmations -> 24-case acceptance job."""
import argparse
import json
from pathlib import Path
import time

from .batch_report import main as batch_report
from .live_confirmation import sha, TARGETS
from .replay import verify_replay


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--batch',type=Path,required=True)
    batch=p.parse_args(argv).batch;reports=batch/'reports'
    def read(path,default):
        try:return json.loads(path.read_text())
        except (OSError,ValueError):return default
    pilot=read(batch/'pilot/reports/confirmation-status.json',{'status':'missing','attempts':[]})
    gate=read(reports/'pilot-gate.json',{'passed':False,'error':'Gate did not finish'})
    supervisor=read(reports/'supervisor-result.json',{})
    replay_errors=[];prefixes=[]
    for result in pilot['attempts']:
        trajectory=result.get('trajectory')
        if not trajectory:continue
        try:
            path=Path(trajectory['path']).resolve(strict=True)
            if not path.is_relative_to((batch/'pilot/episodes').resolve()) or sha(path)!=trajectory['sha256']:raise ValueError('Pilot trajectory fingerprint changed')
            proof=verify_replay(path,batch/'pilot/native-bin/battle-engine-worker',require_complete=False)
            if (proof['transitions'],proof['final_chain'])!=(trajectory['transitions'],trajectory['final_chain']):raise ValueError('Pilot chain mismatch')
            prefixes.append({'case_id':result['case_id'],'replay':proof})
        except Exception as error:replay_errors.append({'case_id':result['case_id'],'error':repr(error)})
    if supervisor.get('stage2_started'):
        batch_report(['--root',str(batch)])
        full=read(reports/'batch24-report.json',{'status':'missing'})
    else:
        full={'status':'not_started','planned':24,'attempted':0,'reason':'Five live confirmations did not all pass'}
        (reports/'batch24-report.json').write_text(json.dumps(full,indent=2)+'\n')
    preservation=read(reports/'preservation-report.json',{'passed':False})
    status=full['status'] if gate.get('passed') else 'pilot_failed_stage2_not_started'
    if not preservation.get('passed') or replay_errors:status='audit_or_replay_error'
    result={'schema':'headless-staged-acceptance-v1','generated_unix':time.time(),'status':status,
            'pilot':pilot,'pilot_gate':gate,'pilot_independent_replays':prefixes,'replay_errors':replay_errors,
            'batch24':full,'preservation':preservation,'supervisor':supervisor,'legacy_training_admission':False}
    (reports/'staged-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    text=['# 原种子确认与 24 局复跑报告','',f'状态：{status}',
          f"第一阶段：已尝试 {len(pilot['attempts'])}/5，目标确认 {sum(r.get('status')=='verified' for r in pilot['attempts'])}/5。",
          f"独立门槛验证：{gate.get('passed',False)}；第二阶段：{full['status']}，已尝试 {full.get('attempted',0)}/24。",
          '', '第一阶段到达目标并完成严格校验后有意结束，轨迹保留删失标记，不伪造游戏胜负。',
          '训练未接入；采集许可在任务收尾时归档关闭。','', '| Case | 原种子 | 结果 | 诊断 |','|---|---:|---|---|']
    by_id={r['case_id']:r for r in pilot['attempts']}
    for case,role,seed,goal in TARGETS:
        r=by_id.get(case,{})
        text.append(f"| {case} | {seed} | {r.get('status','not_attempted')} | {r.get('diagnostic','')} |")
    if not gate.get('passed'):text += ['', '门槛未通过：'+gate.get('error','See pilot diagnostics')]
    if supervisor.get('stage2_started'):text += ['', '24 局逐项结果见同目录 batch24-report.md / batch24-report.json。']
    (reports/'staged-report.md').write_text('\n'.join(text)+'\n',encoding='utf-8')
    print(json.dumps({'status':status,'pilot_gate':gate.get('passed'),'stage2_attempted':full.get('attempted',0)}),flush=True)
    return 0 if preservation.get('passed') and not replay_errors else 1


if __name__=='__main__':raise SystemExit(main())
