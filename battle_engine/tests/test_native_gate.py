import unittest

from battle_engine.tools.native_gate import compare, throughput
from battle_engine.tools.native_evidence import assemble


class NativeGateTest(unittest.TestCase):
    def test_paired_replay_and_quality(self):
        cases=[dict(sha256=str(i),seed=i,role="IRONCLAD",room="MONSTER") for i in range(220)]
        rows=[dict(sha256=c["sha256"],outcome="win",real_engine_checked=True,hp_lost=0,potions_used=0) for c in cases]
        a=dict(native_build="worker",budget_id="locked",runtime_id="frozen",battles=rows)
        self.assertTrue(compare(cases,a,a)["quality_passed"])
        bad=dict(a,battles=[dict(r,hp_lost=1) for r in rows])
        self.assertFalse(compare(cases,a,bad)["quality_passed"])
        self.assertFalse(compare(cases,a,dict(a,battles=[dict(r,real_engine_checked=False) for r in rows]))["quality_passed"])
        with self.assertRaises(ValueError): compare(cases,a,dict(a,budget_id="other"))
        with self.assertRaises(ValueError): compare(cases,a,dict(a,battles=rows[:-1]))

    def test_normal_defeat_counts_technical_abort_does_not(self):
        row=dict(role="R",status="complete",last_floor=3,censored=False,terminal={"outcome":"defeat"},
                 validation={"valid":True,"status":"complete"})
        batch=dict(started_unix=100,ended_unix=3700,results=[dict(row,seed=1),dict(row,seed=2,status="aborted")])
        _,metrics=throughput(batch)
        self.assertEqual((1,1,0,3),(metrics["valid_runs_per_hour"],metrics["aborted"],metrics["run_win_rate"],metrics["mean_terminal_floor"]))

    def test_build_and_settings_bound_to_real_snapshot(self):
        arm=dict(jni_sha256="dll",budgets={"MONSTER":dict(timeout_ms=1,max_expansions=3)},policy="legacy",segment_length=1,tactical_ordering=True,dedup_mode="default")
        cases=[dict(sha256="a",split="holdout",room="MONSTER")]
        row=dict(sha256="a",native_build="dll",settings=dict(search_policy="legacy",segment_length=1,tactical_ordering=True,dedup_mode="default",timeout_ms=1,max_expansions=3))
        self.assertEqual("dll",assemble(arm,cases,[row])["native_build"])
        with self.assertRaises(ValueError): assemble(arm,cases,[])
        with self.assertRaises(ValueError): assemble(dict(arm,segment_length=4),cases,[row])
