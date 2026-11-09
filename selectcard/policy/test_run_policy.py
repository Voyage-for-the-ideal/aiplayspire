"""Causal replay, bounded disk data, checkpoint isolation and BC smoke closure."""
import argparse
import copy
import gzip
import json
from pathlib import Path
import random
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
import torch

from selectcard.src.reconstructor import RunReconstructor
from selectcard.src.map_context import forward_consistent_positions, split_act_segments
from .contracts import CHARACTERS, split_for
from .encoding import FEATURES, build_vocabulary, encode_batch, state_parts
from .from_runs import archive_values, catalog, export_runs, process_file
from .model import SetPolicy
from .profiles import RUN_PROFILE, LIVE_PROFILE, project_example
from .run_replay import CausalRunReplay, MODE_FLAGS, act_map, run_identity
from .run_training import train_shards
from .shards import ShardDataset, verify_dataset, check_coverage
from .train import load_checkpoint
from .training import pilot_member


def raw_run(seed=1, character="IRONCLAD"):
    # Combat symbols at a prefix are independent of the terminal audit fields.
    return dict(seed_played=str(seed), character_chosen=character, ascension_level=0, build_version="2022-12-18",
                victory=True, floor_reached=51, neow_bonus="HUNDRED_GOLD", neow_cost="NONE",
                path_per_floor=["M"]*15+["B", None],
                card_choices=[dict(floor=1,picked="Clash",not_picked=["Anger","Cleave"]),
                              dict(floor=2,picked="SKIP",not_picked=["Anger","Clash","Cleave"]),
                              dict(floor=16,picked="SKIP",not_picked=["Bludgeon","Immolate","Feed"])],
                boss_relics=[dict(picked="Black Blood" if character=="IRONCLAD" else "Fusion Hammer",not_picked=["Sozu","Runic Pyramid"])],
                damage_taken=[dict(floor=16,enemies="The Guardian"),dict(floor=33,enemies="Champ"),dict(floor=50,enemies="Time Eater")],
                current_hp_per_floor=[40]*17, max_hp_per_floor=[80]*17, gold_per_floor=[100]*17,
                master_deck=["TERMINAL_AUDIT_ONLY"], relics=["TERMINAL_AUDIT_ONLY"],
                **{key: 0 if key=="special_seed" else False for key in MODE_FLAGS})


def replay(run):
    return list(RunReconstructor(run, catalog(), mode="policy").replay())


def write_corpus(root, pilot=False):
    selected = []
    for role in CHARACTERS:
        for split in ("train", "val", "test"):
            for seed in range(1, 20000):
                candidate = raw_run(seed, role)
                if split_for(str(seed)) == split and (not pilot or pilot_member(run_identity(candidate))):
                    selected.append(candidate)
                    break
            else:
                raise AssertionError("no cohort member found")
    root.mkdir()
    # gzip + event wrapper + arrays + UTF8 BOM .run / plain json variants.
    with gzip.open(root / "a.json.gz", "wt", encoding="utf-8") as stream:
        json.dump([{"event": row} for row in selected[:6]], stream)
    (root / "b.run").write_text(json.dumps([{"event": row} for row in selected[6:9]]), encoding="utf-8-sig")
    (root / "c.json").write_text(json.dumps(selected[9:]), encoding="utf-8")
    return selected


def arguments(data, output, **overrides):
    defaults = dict(data_dir=data, output_dir=output, mode="bc", device="cpu", stage="confirm", evaluate=False,
                    evaluation_split="val", checkpoint=None, epochs=2, batch_size=16, seed=42, resume=None,
                    epochs_this_run=None, d_model=16, layers=1, heads=4)
    defaults.update(overrides)
    return argparse.Namespace(**defaults)


class ReplayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_pre_action_and_consecutive_choices(self):
        rows = replay(raw_run())
        self.assertEqual(len(rows),4)
        self.assertNotIn("Clash",[c["id"] for c in rows[0]["pre_state"]["deck"]])
        self.assertIn("Clash",[c["id"] for c in rows[1]["pre_state"]["deck"]])
        boss = rows[-1]
        self.assertEqual(boss["decision_type"],"BOSS_REWARD")
        self.assertEqual([r["id"] for r in boss["pre_state"]["relics"]],["Burning Blood"])
        self.assertEqual(boss["pre_state"]["act"],1)
        self.assertEqual(boss["pre_state"]["visible_boss"],"The Guardian")
        self.assertEqual(boss["pre_state"]["path_history"][-1]["resolved_symbol"],"B")
        self.assertEqual(boss["chosen_action_id"],"relic:2")

    def test_no_future_or_terminal_reconciliation(self):
        run = raw_run()
        original = replay(run)[0]
        run.update(master_deck=["Anger+1"]*10, relics=["Astrolabe"], victory=False, killed_by="enemy")
        run["card_choices"][1]["picked"]="Cleave"
        run["path_per_floor"][5:] = ["?"]*10+["B",None]
        changed = replay(run)[0]
        self.assertEqual(original["pre_state"],changed["pre_state"])
        self.assertEqual(original["candidates"],changed["candidates"])
        self.assertFalse(changed["outcome"]["victory"])
        run.pop("killed_by")
        censored=replay(run)[0]
        self.assertEqual(original["pre_state"],censored["pre_state"])
        self.assertIsNone(censored["outcome"]["victory"])
        run["damage_taken"].append(dict(floor=51,enemies="Donu and Deca"))
        self.assertEqual(original["pre_state"],replay(run)[0]["pre_state"])

    def test_identical_choices_retained_and_missing_label_stops(self):
        run=raw_run()
        run["card_choices"]=[dict(floor=1,picked="Anger",not_picked=["Clash","Cleave"])]*2
        rows=replay(run)
        self.assertEqual(len([r for r in rows if r["decision_type"]=="CARD_REWARD"]),2)
        count=lambda row:sum(c["count"] for c in row["pre_state"]["deck"] if c["id"]=="Anger")
        self.assertEqual((count(rows[0]),count(rows[1])),(0,1))
        run=raw_run(); del run["card_choices"][1]["picked"]
        self.assertEqual(len(replay(run)),1)

    def test_skip_bowl_and_uncertain_prefix(self):
        run=raw_run()
        run["relics_obtained"]=[dict(floor=0,key="Singing Bowl")]
        run["card_choices"][0]["picked"]="Singing Bowl"
        rows=replay(run)
        self.assertEqual(rows[0]["chosen_action_id"],"bowl")
        self.assertTrue(any(c["kind"]=="bowl" for c in rows[0]["candidates"]))
        self.assertFalse(rows[0]["pre_state"]["known"]["hp"])
        run["boss_relics"][0]={"not_picked":["Sozu","Runic Pyramid","Fusion Hammer"]}
        self.assertEqual(replay(run)[-1]["chosen_action_id"],"skip")
        run=raw_run();run["relics_obtained"]=[dict(floor=2,key="Whetstone")]
        engine=CausalRunReplay(run,catalog()); rows=list(engine.replay())
        self.assertEqual(len(rows),1)
        self.assertIn("unlogged_relic_mutation",engine.blocked)
        run["neow_bonus"]="UPGRADE_CARD"
        self.assertEqual(replay(run),[])

    def test_eggs_shop_order_and_causal_event_mutations(self):
        run=raw_run()
        run["path_per_floor"][2:4]=["$","?"]
        run["card_choices"].append(dict(floor=5,picked="SKIP",not_picked=["Anger","Clash","Cleave"]))
        run["items_purchased"]=["Cleave","Molten Egg 2","Anger"]
        run["item_purchase_floors"]=[3,3,3]
        run["relics_obtained"]=[dict(floor=3,key="Molten Egg 2")]
        run["event_choices"]=[dict(floor=4,event_name="Living Wall",cards_removed=["Strike_R"],cards_obtained=["Shrug It Off"])]
        rows=replay(run)
        state=next(row for row in rows if row["pre_state"]["floor"]==5)["pre_state"]
        deck={(c["id"],c["upgrades"]):c["count"] for c in state["deck"]}
        self.assertEqual(deck[("Cleave",0)],1)
        self.assertEqual(deck[("Anger",1)],1)
        self.assertEqual(deck[("Strike_R",0)],4)
        self.assertEqual(deck[("Shrug It Off",0)],1)
        candidates=next(row for row in rows if row["pre_state"]["floor"]==5)["candidates"]
        self.assertTrue(all(c["upgrades"]==1 for c in candidates if c["kind"]=="take_card"))

    def test_explicit_neow_targets_and_opaque_lesson_learned(self):
        run=raw_run();run["neow_bonus"]="UPGRADE_CARD"
        run["event_choices"]=[dict(floor=0,cards_upgraded=["Bash"])]
        state=replay(run)[0]["pre_state"]
        self.assertEqual(next(c["upgrades"] for c in state["deck"] if c["id"]=="Bash"),1)
        run=raw_run();run["card_choices"][0]["picked"]="LessonLearned"
        engine=CausalRunReplay(run,catalog())
        self.assertEqual(len(list(engine.replay())),1)
        self.assertEqual(engine.blocked,"unlogged_lesson_learned_upgrade")
        engine=CausalRunReplay(raw_run(),catalog())
        engine.deck += ["Searing Blow+1","Searing Blow+2"]
        engine.upgrade_card("Searing Blow+2",metric_before=True)
        self.assertIn("Searing Blow+3",engine.deck)
        with self.assertRaisesRegex(ValueError,"ambiguous_searing"):
            engine.upgrade_card("Searing Blow")

    def test_map_virtual_boss_double_boss_act4_and_unknown_position(self):
        nodes=act_map(7,1,20)
        self.assertEqual(len(forward_consistent_positions(nodes,["M"]*15+["B","B"])),17)
        path=(["M"]*15+["B",None])*2+["M"]*15+["B","B",None]+["R","$","E","B",None]
        segments=split_act_segments(path)
        self.assertEqual(len(segments),4)
        self.assertEqual(segments[2][1][-2:],["B","B"])
        run=raw_run();run.update(path_per_floor=path,ascension_level=20)
        run["damage_taken"].append(dict(floor=51,enemies="Donu and Deca"))
        engine=CausalRunReplay(run,catalog())
        self.assertEqual(engine.state(40,3,"CARD_REWARD")["visible_boss"],"Time Eater")
        self.assertEqual(engine.state(51,3,"CARD_REWARD")["visible_boss"],"Donu and Deca")
        state=engine.state(55,4,"CARD_REWARD")
        self.assertEqual(state["current_map_node"],dict(x=3,y=2))
        self.assertTrue(state["known"]["map"])
        state=CausalRunReplay(raw_run(7),catalog()).state(1,1,"CARD_REWARD")
        self.assertFalse(state["known"]["position"])
        self.assertEqual(state["current_map_node"],{})
        for token,numeric in state_parts(state):
            if token.startswith("map:"):
                self.assertEqual(numeric[FEATURES.index("legal")],0)

    def test_optional_masks_projection_and_model_equivariance(self):
        row=replay(raw_run())[0]
        row["pre_state"]["deck"][0].update(misc=100,is_bottled=True)
        projected=project_example(row,RUN_PROFILE)
        self.assertNotIn("misc",projected["pre_state"]["deck"][0])
        self.assertIn("context:position:unknown",[t for t,_ in state_parts(projected["pre_state"])])
        row=projected
        row["candidates"].append(dict(action_id="illegal",kind="take_card",entity_id="Anger",legal=False))
        vocab=build_vocabulary([row]);model=SetPolicy(len(vocab),d_model=16,layers=1,heads=4).eval()
        order=list(reversed(range(len(row["candidates"]))))
        other=dict(row,candidates=[row["candidates"][i] for i in order])
        with torch.no_grad():
            p=model(encode_batch([row],vocab)).softmax(-1)[0]
            q=model(encode_batch([other],vocab)).softmax(-1)[0]
        torch.testing.assert_close(q,p[order],atol=1e-6,rtol=1e-5)
        self.assertEqual(p[-1],0)


class PipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_archive_shards_streaming_train_resume_reload_predict(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/"raw";write_corpus(source)
            data=root/"data"
            manifest=export_runs(source,data,stage="confirm",shard_size=3)
            self.assertEqual(manifest["report"]["samples"],48)
            check_coverage(data)
            self.assertEqual(verify_dataset(data)["fingerprint"],manifest["fingerprint"])
            # Reuse is explicit and fingerprints all inputs, code, index/shards.
            self.assertEqual(export_runs(source,data,stage="confirm",shard_size=3)["fingerprint"],manifest["fingerprint"])
            import os
            previous=Path.cwd()
            try:
                os.chdir(root)
                relative=ShardDataset(Path("data"),"val")
                self.assertEqual(len(relative),16)
                relative.close()
            finally:
                os.chdir(previous)
            dataset=ShardDataset(data,"train",cache_size=2)
            self.assertEqual(len(dataset),16)
            self.assertEqual(len(dataset[:3]),3)
            list(dataset)
            self.assertLessEqual(len(dataset.handles),2)
            self.assertFalse(isinstance(dataset.balanced_indices(100,random.Random(1)),list))
            self.assertEqual(len(list(dataset.balanced_indices(30,random.Random(1)))),30)
            dataset.close()
            # Training must not read sealed test examples.
            original_get=ShardDataset._read
            def guarded(obj, metadata):
                self.assertNotEqual(obj.split,"test")
                return original_get(obj,metadata)
            with patch.object(ShardDataset,"_read",guarded):
                train_shards(arguments(data,root/"full"))
                train_shards(arguments(data,root/"resumed",epochs_this_run=1))
                train_shards(arguments(data,root/"resumed",resume=root/"resumed/last.pt"))
            model,value=load_checkpoint(root/"full/policy.pt",expected_profile=RUN_PROFILE)
            _,resumed=load_checkpoint(root/"resumed/policy.pt")
            for key,tensor in value["policy"].items():
                torch.testing.assert_close(tensor,resumed["policy"][key],rtol=0,atol=0)
            self.assertEqual(value["epoch"],2)
            self.assertTrue((root/"full/best.pt").is_file())
            self.assertTrue((root/"full/curves.svg").is_file())
            rows=ShardDataset(data,"val")
            model.eval()
            with torch.no_grad():
                self.assertTrue(torch.isfinite(model(encode_batch(rows[:2],value["vocabulary"]))).all())
            rows.close()
            with self.assertRaisesRegex(ValueError,"input profile"):
                load_checkpoint(root/"full/policy.pt",expected_profile=LIVE_PROFILE)
            with self.assertRaisesRegex(ValueError,"resume requires identical"):
                train_shards(arguments(data,root/"resumed",resume=root/"resumed/last.pt",batch_size=8))
            report=json.loads((root/"full/metrics.json").read_text())
            self.assertEqual(len(report["history"]),2)
            self.assertIn("frequency_nll",report["validation"]["all"])
            self.assertIn("ascension_band:0-9",report["validation"])
            # Corruption cannot reuse a stale manifest or train silently.
            with (data/"shard-00000.jsonl").open("ab") as f:f.write(b"bad\n")
            with self.assertRaisesRegex(ValueError,"fingerprint mismatch"):
                verify_dataset(data)

    def test_pilot_once_dedup_and_missing_confirm_coverage(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/"raw";runs=write_corpus(source,pilot=True)
            (source/"duplicate.run").write_text(json.dumps(runs[0]),encoding="utf-8")
            manifest=export_runs(source,root/"pilot",stage="pilot",shard_size=4)
            self.assertEqual(manifest["report"]["samples"],48)
            self.assertEqual(manifest["report"]["reasons"]["duplicate_run"],1)
            self.assertEqual(manifest["cohort_fraction"],.01)
            train_shards(arguments(root/"pilot",root/"trained",stage="pilot",epochs=1))
            self.assertEqual(json.loads((root/"trained/metrics.json").read_text())["validation"]["all"]["count"],16)
            sparse=root/"sparse";sparse.mkdir()
            (sparse/"one.run").write_text(json.dumps(raw_run()),encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"missing required coverage"):
                export_runs(sparse,root/"bad",stage="confirm")
            self.assertTrue((root/"bad/coverage.json").is_file())

    def test_transport_duplicates_and_quarantine(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            run=raw_run();second=dict(run,play_id="different",timestamp=999)
            path=root/"double.run";path.write_text(json.dumps([run,second]),encoding="utf-8")
            rows,reports=process_file((path,"confirm"))
            self.assertEqual(len(rows),4)
            self.assertEqual(reports[-1]["status"],"duplicate_in_archive")
            path.write_text("{broken",encoding="utf-8")
            self.assertEqual(process_file((path,"confirm"))[1][0]["status"],"invalid_archive")

    def test_gpu_unavailable_fails_before_allocating_outputs(self):
        with patch("torch.cuda.is_available",return_value=False):
            with self.assertRaisesRegex(RuntimeError,"refusing CPU fallback"):
                train_shards(arguments(Path("missing"),Path("unused"),device="cuda"))

    def test_archive_arrays_are_incremental_and_grouped_by_seed(self):
        import io
        class SmallReads(io.StringIO):
            def read(self,n=-1):
                if n<0:
                    return super().read(n)
                return super().read(min(n,7))
        values=[dict(event=raw_run(i)) for i in range(5)]
        self.assertEqual(list(archive_values(SmallReads(json.dumps(values)))),values)
        with self.assertRaises(ValueError):
            list(archive_values(io.StringIO('[{},]')))
        for char in CHARACTERS:
            row=replay(raw_run(42,char))[0]
            self.assertEqual(row["split"],split_for("42"))


if __name__=="__main__":
    unittest.main()
