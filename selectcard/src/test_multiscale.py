"""Synthetic, archive-independent format-12 regressions and end-to-end smoke."""
import copy
import gzip
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
import torch

from config import Config
from data_contract import active_bins, multiscale_targets, numpy_targets, STAGE_ENDPOINTS
from dataset import GlobalFeatureEncoder, build_training_artifacts
from map_context import RunMapContextBuilder
from multiscale import ValueOutputs, distribution
from value_loss import value_loss_components
from value_metrics import nll_metrics
from outcome_statistics import validate_statistics


class ContractTests(unittest.TestCase):
    def test_exact_active_bins_and_completed_boundaries(self):
        expected = {5: (6,9,12,15,17,34,51,57), 17: (20,23,26,29,32,34,51,57),
                    34: (37,40,43,46,49,51,57), 51: (54,57), 57: ()}
        for floor, ends in expected.items():
            self.assertEqual(tuple(b[3] for b in active_bins(floor)), ends)
        self.assertTrue(all(kind == "coarse" for _,kind,_,end in active_bins(5) if end > 17))

    def test_all_boundaries_and_vectorized_labels_match(self):
        for mode in ("full_fine", "multiscale"):
            for floor in range(58):
                for known in (False, True):
                    terminal = np.arange(floor,58,dtype=float)
                    y,m = numpy_targets(np.full(len(terminal),floor), terminal,
                                         np.full(len(terminal),known), np.zeros(len(terminal)), mode)
                    for i,t in enumerate(terminal):
                        py,pm = multiscale_targets(floor,t,known,False,mode)
                        np.testing.assert_array_equal(y[i],py)
                        np.testing.assert_array_equal(m[i],pm)
                        self.assertLessEqual(sum(np.asarray(py[:-1])*np.asarray(pm[:-1])),1)
        y,m = multiscale_targets(49,57,True,True)
        self.assertEqual(sum(y[:-1]),0)
        self.assertEqual(y[-1],1)

    def test_far_bins_merge_and_censoring_preserves_unknown(self):
        y,m = multiscale_targets(5,36,True,False)
        self.assertEqual(y[21],1)  # future third stage [34,51)
        self.assertFalse(m[12])  # no fabricated fine stop_before_37
        y,m = multiscale_targets(5,36,False,False)
        self.assertTrue(m[20])  # reached 34
        self.assertFalse(m[21])
        self.assertFalse(m[-1])

    def test_key_persists_across_acts_and_missing_maps(self):
        b = RunMapContextBuilder.__new__(RunMapContextBuilder)
        b.recall, b.emerald_floors, b.act_maps = None,{1:8},{1:[]}
        for act,floor in ((1,10),(2,20),(3,35)):
            values,known = b._key_context(act,floor)
            self.assertEqual((values[1],known[1]),(1,1))
            self.assertEqual((values[2],known[2]),(0,0))
        b.emerald_floors = {}
        b.act_maps = {2:[]}
        self.assertEqual(b._key_context(2,20)[1][1],0)

    def test_global_encoder_separates_late_floors_and_matches_row(self):
        encoder = GlobalFeatureEncoder(82,95,555)
        state = dict(hp=50,max_hp=80,gold=99,ascension=20)
        values = [encoder.transform_state(dict(state,floor=f)) for f in (50,51,54,55,56)]
        self.assertEqual(len({tuple(v) for v in values}),5)
        self.assertTrue(all(len(v)==11 for v in values))
        self.assertEqual(values[-1],encoder.transform_row(dict(state,floor=56)))

    def test_a20_raw_normal_win_heart_death_and_heart_win(self):
        from test_data_pipeline import standard_run, STARTER_DECK
        from data_pipeline import process_run
        from content_catalog import VanillaContentCatalog
        catalog = VanillaContentCatalog.from_repo()
        heart_name = next(e for e in catalog.enemies if e.lower() in {"the heart","corrupt heart","the corrupt heart"})
        for reached,victory,enemy,expected in ((51,True,"Time Eater",51),
                                             (56,False,heart_name,56),(56,True,heart_name,57)):
            run = standard_run(ascension_level=20,is_ascension_mode=True,
                master_deck=STARTER_DECK+["AscendersBane"],floor_reached=reached,
                current_hp_per_floor=[70]*reached,max_hp_per_floor=[80]*reached,
                gold_per_floor=[99]*reached,path_per_floor=["M"]*reached,path_taken=["M"]*reached,
                victory=victory,killed_by=None if victory else enemy,
                damage_taken=[dict(floor=reached,enemies=enemy,damage=10,turns=2)])
            rows = process_run(run,catalog)["rows"]
            self.assertTrue(rows)
            self.assertTrue(all(row["terminal_known"] for row in rows))
            self.assertTrue(all(row["terminal_floor"]==expected for row in rows))
            self.assertTrue(all(row["heart_victory"] == (victory and reached==56) for row in rows))


class DistributionTests(unittest.TestCase):
    def test_mass_bounds_survival_and_inactive_invariance(self):
        torch.manual_seed(5)
        for mode in ("full_fine","multiscale"):
            floors = torch.arange(58,dtype=torch.float32)
            logits = torch.randn(58,47,requires_grad=True)
            d = distribution(logits,floors,mode)
            torch.testing.assert_close(d["stop_mass"].sum(1)+d["final_survival"],torch.ones(58))
            self.assertTrue((d["expected_floor"] >= floors-1e-5).all())
            self.assertTrue((d["expected_floor"] <= 57+1e-5).all())
            self.assertTrue((d["heart_probability"] <= d["final_survival"]).all())
            for row in range(58):
                ids = [i for i,_,_,_ in active_bins(row,mode)]
                alive = d["survival"][row,ids]
                self.assertTrue((alive[1:] <= alive[:-1]).all())
            changed = logits.detach().clone()
            changed[:,:23][~d["active"]] = 100
            changed[:,23:46][~d["active"]] = -100
            other = distribution(changed,floors,mode)
            torch.testing.assert_close(d["expected_floor"],other["expected_floor"])
            torch.testing.assert_close(d["milestones"],other["milestones"])
            d["expected_floor"].sum().backward()
            self.assertTrue(torch.isfinite(logits.grad).all())
            self.assertEqual(float(logits.grad[:,:23][~d["active"]].abs().sum()),0)

    def test_only_observed_stop_position_receives_gradient(self):
        for known in (True,False):
            logits = torch.zeros(1,47,requires_grad=True)
            y,m = multiscale_targets(5,36,known,False)
            loss = value_loss_components(logits,torch.tensor([y]),torch.tensor([m]).float(),
                torch.tensor([5.]),torch.zeros(1,11),torch.ones(6),[.2]*24,
                terminal_floors=torch.tensor([36.]))
            loss["total"].backward()
            nonzero = logits.grad[0,23:46].nonzero().flatten().tolist()
            self.assertEqual(nonzero,[21] if known else [])
            self.assertEqual(float(logits.grad[0,12]),0)

    def test_extreme_heart_logits_and_empty_masks_are_finite(self):
        for x in (-1000.,1000.):
            logits = torch.full((1,47),x,requires_grad=True)
            for include in (0.,1.):
                loss = value_loss_components(logits,torch.zeros(1,24),torch.full((1,24),include),
                    torch.tensor([5.]),torch.zeros(1,11),torch.ones(6),[0.]*24,
                    terminal_floors=torch.tensor([5.]))["total"]
                grad = torch.autograd.grad(loss,logits,retain_graph=True)[0]
                self.assertTrue(torch.isfinite(loss))
                self.assertTrue(torch.isfinite(grad).all())

    def test_common_score_does_not_use_training_weights(self):
        p = np.array([[.9,.8,.7,.6,.2],[.8,.7,.6,.5,.1]])
        y = np.array([[1,1,1,1,1],[0,0,0,0,0]])
        with patch.object(Config,"HEART_HEAD_LOSS_WEIGHT",1.):
            first = nll_metrics(p,y,np.ones_like(y),[.5]*5)
        with patch.multiple(Config,HEART_HEAD_LOSS_WEIGHT=.25,BASELINE_ENTROPY_FLOOR=.9):
            other = nll_metrics(p,y,np.ones_like(y),[.5]*5)
        self.assertEqual(first,other)


class PipelineTests(unittest.TestCase):
    def test_rebuild_ascension_option_defaults_and_requires_named_output(self):
        from rebuild_multiscale import main
        with tempfile.TemporaryDirectory() as root:
            data = Path(root) / "new_data"
            with patch("rebuild_multiscale.build_dataset") as build, patch("rebuild_multiscale.generate") as generate:
                main(["--input-dir", "raw", "--output-dir", str(data), "--workers", "2"])
                build.assert_called_once_with("raw", data.with_name("new_data_base"), workers=2, min_ascension=10)
                generate.assert_called_once()
            with patch("rebuild_multiscale.build_dataset") as build, patch("rebuild_multiscale.generate"):
                main(["--input-dir", "raw", "--output-dir", str(data), "--min-ascension", "15"])
                build.assert_called_once_with("raw", data.with_name("new_data_base"), workers=None, min_ascension=15)
            with patch("sys.stderr"), self.assertRaises(SystemExit):
                main(["--input-dir", "raw", "--min-ascension", "15"])

    def test_a15_rebuild_and_audit_end_to_end(self):
        import pandas as pd
        from test_data_pipeline import standard_run, STARTER_DECK
        from data_pipeline import assign_split, stable_run_id
        from inspect_multiscale import inspect
        from rebuild_multiscale import main
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source, output = root / "raw", root / "a15"
            source.mkdir()
            runs = []
            for ascension, split in ((14, "train"), (15, "train"), (20, "val")):
                run = standard_run(
                    is_ascension_mode=True, ascension_level=ascension,
                    master_deck=STARTER_DECK + ["AscendersBane"],
                    seed_played="4537055902605411270", floor_reached=16,
                    current_hp_per_floor=[70]*16, max_hp_per_floor=[80]*16,
                    gold_per_floor=[99]*16, path_per_floor=["M"]*16,
                    path_taken=["M"]*16, killed_by="The Guardian",
                    damage_taken=[dict(floor=16, enemies="The Guardian", damage=10, turns=2)],
                )
                index = 0
                while assign_split(stable_run_id(run)) != split:
                    run["play_id"] = f"{ascension}-{split}-{index}"
                    index += 1
                runs.append(run)
            with gzip.open(source / "runs.json.gz", "wt", encoding="utf-8") as handle:
                json.dump(runs, handle)
            main(["--input-dir", str(source), "--output-dir", str(output), "--workers", "1",
                  "--min-ascension", "15", "--minimum-boss-samples", "1"])
            manifest = json.loads((output / "dataset_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["selection"]["min_ascension"], 15)
            self.assertEqual(manifest["output"]["accepted_runs"], 2)
            self.assertEqual(manifest["rejections"]["below_min_ascension_15"], 1)
            for split, ascension in (("train", 15), ("val", 20)):
                frame = pd.read_parquet(next(output.glob(f"{split}_valid_chunk_*.parquet")))
                self.assertEqual(set(frame["ascension"]), {ascension})
                self.assertIn("visible_boss", frame.columns)
                report = inspect(output, split, min_ascension=15)
                self.assertEqual((report["ascension_min"], report["ascension_max"]), (ascension, ascension))
            with self.assertRaisesRegex(ValueError, "manifest must specify min_ascension=10"):
                inspect(output, "train", min_ascension=10)
            train_shard = next(output.glob("train_valid_chunk_*.parquet"))
            frame = pd.read_parquet(train_shard)
            frame["ascension"] = 14
            frame.to_parquet(train_shard, index=False)
            with self.assertRaisesRegex(ValueError, "ascension"):
                inspect(output, "train", min_ascension=15)
            with self.assertRaises(FileExistsError):
                main(["--input-dir", str(source), "--output-dir", str(output), "--min-ascension", "15"])

    def test_a10_audit_checks_manifest_and_every_train_val_row(self):
        import pandas as pd
        from test_data_pipeline import standard_run, STARTER_DECK
        from data_pipeline import build_dataset, assign_split, stable_run_id
        from inspect_multiscale import inspect
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source, data = root / "raw", root / "data"
            source.mkdir()
            runs = []
            for split in ("train", "val"):
                run = standard_run(is_ascension_mode=True, ascension_level=10,
                    master_deck=STARTER_DECK + ["AscendersBane"],
                    seed_played="-3775500262246337531")
                index = 0
                while assign_split(stable_run_id(run)) != split:
                    run["play_id"] = f"{split}-{index}"
                    index += 1
                runs.append(run)
            with gzip.open(source / "runs.json.gz", "wt", encoding="utf-8") as handle:
                json.dump(runs, handle)
            build_dataset(source, data, workers=1, min_ascension=10)
            for split in ("train", "val"):
                report = inspect(data, split, min_ascension=10)
                self.assertEqual((report["ascension_min"], report["ascension_max"]), (10, 10))
                shard = next(data.glob(f"{split}_valid_chunk_*.parquet"))
                original = pd.read_parquet(shard)
                for bad in (9, 21, float("nan"), 10.5):
                    altered = original.copy()
                    altered["ascension"] = bad
                    altered.to_parquet(shard, index=False)
                    with self.subTest(split=split, ascension=bad), self.assertRaisesRegex(ValueError, "ascension"):
                        inspect(data, split, min_ascension=10)
                original.to_parquet(shard, index=False)
            manifest_path = data / "dataset_manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["selection"]["min_ascension"] = 0
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "manifest must specify min_ascension=10"):
                inspect(data, "train", min_ascension=10)

    def test_build_train_reload_evaluate_and_recommend(self):
        from test_data_pipeline import standard_run
        from data_pipeline import build_dataset, assign_split, stable_run_id
        from train import train_model
        from evaluation_v4 import evaluate_checkpoint
        from checkpointing import load_checkpoint
        from inference import STSInferenceEngine
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            source, data, trained = root/"raw",root/"data",root/"trained"
            source.mkdir()
            runs = []
            for split in ("train","val"):
                for reached in (1,57):
                    raw = standard_run(floor_reached=reached,
                        seed_played="-3775500262246337531", current_hp_per_floor=[70]*reached,
                        max_hp_per_floor=[80]*reached,gold_per_floor=[99]*reached,
                        path_per_floor=["M"]*reached,path_taken=["M"]*reached)
                    index = 0
                    while assign_split(stable_run_id(raw)) != split:
                        raw["play_id"] = f"{split}-{reached}-{index}"
                        index += 1
                    runs.append({"event":raw})
            with gzip.open(source/"runs.json.gz","wt",encoding="utf-8") as out:
                json.dump(runs,out)
            build_dataset(source,data,workers=1)
            from inspect_multiscale import inspect
            self.assertEqual(inspect(data)["states"],2)
            _,_,stats = build_training_artifacts(data,include_baselines=True)
            broken = copy.deepcopy(stats)
            broken["global"]["rates"][0] = .12345
            with self.assertRaisesRegex(ValueError,"rate disagrees"):
                validate_statistics(broken,data)
            # Internally consistent corrupted priors must still fail the manifest check.
            broken = copy.deepcopy(stats)
            group = broken["resolution_statistics"]["multiscale"]
            observed = next(i for i, valid in enumerate(group["valid"]) if valid)
            group["positive"][observed] = 0 if group["positive"][observed] else group["valid"][observed]
            group["rates"][observed] = group["positive"][observed] / group["valid"][observed]
            with self.assertRaisesRegex(ValueError,"counts disagree with manifest"):
                validate_statistics(broken,data)
            cache = data/"training_artifacts.json"
            old = json.loads(cache.read_text());old["cache_version"]=3
            cache.write_text(json.dumps(old))
            build_training_artifacts(data)
            self.assertEqual(json.loads(cache.read_text())["cache_version"],4)
            settings = dict(DATA_DIR=str(data),CHECKPOINT_DIR=str(trained),D_MODEL=16,N_LAYERS=1,
                EPOCHS=4,BATCH_SIZE=4,COSINE_DECAY_EPOCHS=4,EARLY_STOPPING_PATIENCE=0,
                USE_MAP_CONTEXT=True,DEVICE="cpu",DROPOUT=0.,RESOLUTION_MODE="multiscale",
                VALUE_LOSS_MODE="multiscale",RUN_FRACTION=1.,
                EXPERIMENT_METADATA={"stage":"confirm","settings":{"RUN_FRACTION":1.}})
            with patch.multiple(Config,**settings),patch.dict("os.environ",{"STS_DATALOADER_WORKERS":"0"}):
                train_model()
                path = trained/Config.CHECKPOINT_NAME
                original = path.read_bytes()
                model,_,_ = load_checkpoint(path)
                self.assertEqual(model["format_version"],12)
                with patch.multiple(Config,RESOLUTION_MODE="full_fine",FAR_LOSS_WEIGHT=99.,MEAN_LOSS_WEIGHT=99.,HEART_HEAD_LOSS_WEIGHT=9.):
                    evaluate_checkpoint(path,data,root/"evaluation",device_name="cpu")
                    self.assertEqual(Config.RESOLUTION_MODE,"full_fine")
                self.assertEqual(original,path.read_bytes())
                self.assertFalse(model["metadata"]["held_out_test_evaluated"])
                engine = STSInferenceEngine(path)
                state = dict(deck=["Strike_R","Bash"],relics=["Burning Blood"],floor=5,
                    hp=70,max_hp=80,gold=99,ascension=0,character="IRONCLAD",visible_boss="NO_BOSS")
                result = engine.evaluate_state_distribution(state)
                self.assertTrue(0 <= result["value"] <= 1)
                self.assertEqual(tuple(result["hazards"]),(6,9,12,15,17,34,51,57))
                self.assertEqual(engine.recommend_choice(state,[{"action":"skip"}]),{"action":"skip"})


if __name__ == "__main__":
    unittest.main()
