"""Synthetic V4 contracts, architecture ablations and paired evaluation tests."""

import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pandas as pd
import torch

from benchmark_v4 import benchmark_model, synthetic_inputs
from checkpointing import create_checkpoint, load_checkpoint, save_checkpoint
from config import Config
from dataset import GlobalFeatureEncoder
from encoding import ItemVocabulary, encode_items
from evaluation_v4 import compare_exports, fingerprint
from experiments import BASELINE, EXPERIMENTS, configure_experiment, selected_run
from map_context import (
    FLAG_CURRENT_CANDIDATE, MAP_GRID_CELLS, RunMapContextBuilder,
    decode_map_features, inference_context, nodes_as_payload, pack_graph,
)
from model import STSValueNetwork, SetAttention
from model_components import MapNodeEncoder
from sts_mapgen import MapRoomNode, MapEdge, MONSTER, REST, ELITE
from train import model_config


def small_model(**kwargs):
    args = dict(vocab_size=32, d_model=32, n_layers=1, n_heads=4,
                dropout=0.0, use_map_context=True)
    args.update(kwargs)
    return STSValueNetwork(**args)


def graph_fixture():
    nodes = [[MapRoomNode(x, y) for x in range(7)] for y in range(15)]
    for x, y, room in ((2, 0, MONSTER), (1, 1, MONSTER), (3, 1, REST),
                       (1, 2, ELITE), (2, 2, ELITE), (2, 14, REST)):
        nodes[y][x].room = room
    for x, y, dx, dy in ((2, 0, 1, 1), (2, 0, 3, 1), (1, 1, 1, 2),
                         (1, 1, 2, 2), (3, 1, 2, 2), (2, 14, 3, 16)):
        nodes[y][x].add_edge(MapEdge(x, y, dx, dy))
        if dy < 15:
            nodes[dy][dx].add_parent(nodes[y][x])
    # Simulate the game's duplicated parent bookkeeping.
    nodes[2][2].add_parent(nodes[1][1])
    return nodes


class GraphContractTests(unittest.TestCase):
    def test_all_coordinates_match_python_decoder(self):
        model = small_model()
        flags = torch.arange(105).view(1, -1)
        decoded = model._decode_map_flags(flags)[0]
        _, expected = decode_map_features([1] * 105, flags[0].tolist())
        torch.testing.assert_close(decoded, torch.tensor(expected))
        self.assertEqual(torch.unique(decoded[:, 3:5], dim=0).size(0), 105)
        torch.testing.assert_close(decoded[38, 3:5], torch.tensor([0.5, 5 / 14]))

    def test_archived_and_live_graph_encoding_identical(self):
        nodes = graph_fixture()
        builder = RunMapContextBuilder({"path_per_floor": ["M", "R", "E"], "seed_played": "invalid"})
        builder.act_maps = {1: nodes}
        archived = builder.context_for_floor(3)
        live = inference_context({"act": 1, "map_nodes": nodes_as_payload(nodes),
                                  "current_node": (2, 2), "history": ["M", "R"]})
        for name in ("map_rooms", "map_flags", "map_outgoing_mask", "history_rooms"):
            self.assertEqual(archived[name], live[name], name)
        self.assertEqual((live["map_flags"][2 * 7 + 2] >> 6) & 3, 2)
        self.assertEqual((live["map_flags"][14 * 7 + 2] >> 4) & 3, 1)
        self.assertEqual(live["map_outgoing_mask"][14 * 7 + 2], 0)

    def test_multiple_candidates_current_row_only_and_prefix_only(self):
        builder = RunMapContextBuilder({"path_per_floor": ["M", "M", "E", "R"], "seed_played": "invalid"})
        builder.act_maps = {1: graph_fixture()}
        first = builder.context_for_floor(3)
        candidates = [i for i, bits in enumerate(first["map_flags"]) if bits & FLAG_CURRENT_CANDIDATE]
        self.assertEqual(candidates, [15, 16])
        builder.segments[0][1][3] = "$"
        self.assertEqual(first, builder.context_for_floor(3))
        self.assertEqual(first["history_rooms"][:2], [1, 1])

    def test_missing_position_never_guessed_from_floor(self):
        result = inference_context({"act": 1, "floor_in_act": 2,
                                    "map_nodes": nodes_as_payload(graph_fixture())})
        self.assertFalse(any(f & FLAG_CURRENT_CANDIDATE for f in result["map_flags"]))

    def test_duplicate_edges_and_parent_bookkeeping_do_not_change_packing(self):
        nodes = nodes_as_payload(graph_fixture())
        expected = pack_graph(nodes, [(2, 0)])
        nodes[0]["children"] *= 3
        self.assertEqual(expected, pack_graph(nodes, [(2, 0)]))

    def test_non_vanilla_edges_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsupported map edge"):
            pack_graph([{"x": 0, "y": 0, "symbol": "M", "children": [{"x": 3, "y": 1}]},
                        {"x": 3, "y": 1, "symbol": "R"}])

    def test_item_encoding_and_73_limit_unchanged(self):
        vocabulary = ItemVocabulary()
        for i in range(90):
            vocabulary.add(f"item{i:02}")
        vocabulary.freeze()
        deck = [f"item{i:02}" for i in range(90)] + ["item00"] * 4
        tokens, upgrades, counts = encode_items(deck, [], vocabulary, 73, 15, 10)
        self.assertEqual(len(tokens), 73)
        self.assertEqual(tokens, sorted(vocabulary.get_id(n) for n in set(deck))[:73])
        self.assertEqual(counts[0], 5)
        self.assertEqual(upgrades, [0] * 73)


class ArchitectureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(1)

    def test_map_history_ablation_is_a_single_variable_control(self):
        torch.manual_seed(42)
        baseline = small_model(n_layers=2).eval()
        ablated = small_model(n_layers=2, ablate_map_history=True).eval()
        self.assertEqual(set(baseline.state_dict()), set(ablated.state_dict()))
        self.assertEqual(sum(p.numel() for p in baseline.parameters()),
                         sum(p.numel() for p in ablated.parameters()))
        ablated.load_state_dict(baseline.state_dict(), strict=True)
        args, ctx = synthetic_inputs(baseline, 2, torch.device("cpu"))
        ctx.update(history_rooms=torch.ones(2, 8, dtype=torch.long),
                   history_dist=torch.arange(8).expand(2, -1))
        expected, expected_keys = ablated(*args, **ctx, return_keys=True)
        for fields in (("map_rooms", "map_flags", "map_outgoing_mask"),
                       ("history_rooms", "history_dist")):
            changed = {name: tensor.clone() for name, tensor in ctx.items()}
            for name in fields:
                changed[name].zero_()
            with self.subTest(fields=fields):
                self.assertFalse(torch.allclose(baseline(*args, **ctx).flat,
                                                baseline(*args, **changed).flat))
                actual, actual_keys = ablated(*args, **changed, return_keys=True)
                torch.testing.assert_close(expected.flat, actual.flat, rtol=0, atol=0)
                torch.testing.assert_close(expected_keys, actual_keys, rtol=0, atol=0)
        changed = {name: tensor.clone() for name, tensor in ctx.items()}
        changed["keys_features"].fill_(1)
        actual, actual_keys = ablated(*args, **changed, return_keys=True)
        self.assertFalse(torch.allclose(expected.flat, actual.flat))
        self.assertFalse(torch.allclose(expected_keys, actual_keys))
        _, map_tokens, history_tokens, key_mask, map_mask, history_mask = ablated._context_tokens(
            ctx["keys_features"], ctx["map_rooms"], ctx["map_flags"],
            ctx["history_rooms"], ctx["history_dist"], 2, torch.device("cpu"),
            ctx["map_outgoing_mask"])
        self.assertEqual((map_tokens.size(1), history_tokens.size(1)), (105, 8))
        self.assertFalse(key_mask.any())
        self.assertTrue(map_mask.all() and history_mask.all())
        (expected.flat.square().sum() + expected_keys.square().sum()).backward()
        for module in (ablated.map_room_emb, ablated.map_flag_proj, ablated.hist_dist_emb):
            for parameter in module.parameters():
                self.assertTrue(parameter.grad is None or parameter.grad.eq(0).all())
        self.assertGreater(ablated.keys_mlp[0].weight.grad.abs().sum().item()
                           + ablated.keys_mlp[0].bias.grad.abs().sum().item(), 0)

    def test_ablation_rejects_incompatible_architectures(self):
        for variant in (dict(use_map_context=False), dict(map_encoder="dag"),
                        dict(map_latents=16), dict(coordinate_embedding="plr")):
            with self.subTest(variant=variant), self.assertRaisesRegex(ValueError, "ablation requires"):
                small_model(ablate_map_history=True, **variant)

    def test_ablation_flag_round_trips_through_checkpoint(self):
        model = small_model(ablate_map_history=True).eval()
        args, ctx = synthetic_inputs(model, 2, torch.device("cpu"))
        vocabulary = ItemVocabulary()
        for i in range(30):
            vocabulary.add(f"item{i}")
        vocabulary.freeze()
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "ablated.pth"
            save_checkpoint(path, model, model.model_config, vocabulary, GlobalFeatureEncoder())
            checkpoint, _, _ = load_checkpoint(path)
            self.assertEqual(checkpoint["format_version"], 12)
            self.assertIs(checkpoint["model_config"]["ablate_map_history"], True)
            restored = STSValueNetwork(**checkpoint["model_config"]).eval()
            restored.load_state_dict(checkpoint["model_state_dict"], strict=True)
            torch.testing.assert_close(model(*args, **ctx).flat, restored(*args, **ctx).flat,
                                       rtol=0, atol=0)
            # Format-12 checkpoints predating the flag still default to the control.
            baseline = small_model().eval()
            legacy = create_checkpoint(baseline, baseline.model_config, vocabulary, GlobalFeatureEncoder())
            legacy["model_config"].pop("ablate_map_history")
            path = Path(root) / "legacy-format12.pth"
            torch.save(legacy, path)
            checkpoint, _, _ = load_checkpoint(path)
            restored = STSValueNetwork(**checkpoint["model_config"]).eval()
            self.assertIs(restored.ablate_map_history, False)
            restored.load_state_dict(checkpoint["model_state_dict"], strict=True)
            torch.testing.assert_close(baseline(*args, **ctx).flat, restored(*args, **ctx).flat,
                                       rtol=0, atol=0)

    def test_mha_without_weights_forward_and_gradient_equivalence(self):
        torch.manual_seed(42)
        layer = SetAttention(32, 4, dropout=0.0)
        x = torch.randn(2, 7, 32, requires_grad=True)
        pad = torch.tensor([[False] * 5 + [True] * 2] * 2)
        reference = layer.mha(x, x, x, key_padding_mask=pad, need_weights=True)[0]
        grad = torch.autograd.grad(reference.square().sum(), x)[0]
        actual = layer.mha(x, x, x, key_padding_mask=pad, need_weights=False)[0]
        actual_grad = torch.autograd.grad(actual.square().sum(), x)[0]
        torch.testing.assert_close(reference, actual, atol=2e-6, rtol=2e-5)
        torch.testing.assert_close(grad, actual_grad, atol=2e-6, rtol=2e-5)

    def test_each_component_forward_backward_and_checkpoint(self):
        variants = [dict(ffn_type="gelu"), dict(ffn_type="swiglu"), dict(norm_type="rmsnorm"),
                    dict(map_encoder="edge"), dict(map_encoder="node_mlp"), dict(map_encoder="dag"),
                    dict(map_latents=16), dict(map_latents=32), dict(coordinate_embedding="plr"),
                    dict(pooling="cls_mean"), dict(register_tokens=2), dict(register_tokens=4),
                    dict(map_encoder="dag", map_latents=16, pooling="cls_mean", norm_type="rmsnorm"),
                    dict(global_conditioning="late_concat", pooling="cls_mean")]
        vocab = ItemVocabulary()
        for i in range(30):
            vocab.add(f"item{i}")
        vocab.freeze()
        with tempfile.TemporaryDirectory() as root:
            for i, variant in enumerate(variants):
                with self.subTest(variant=variant):
                    model = small_model(**variant)
                    args, ctx = synthetic_inputs(model, 2, torch.device("cpu"))
                    logits, keys = model(*args, **ctx, return_keys=True)
                    self.assertEqual(tuple(logits.shape), (2, 47))
                    self.assertEqual(tuple(keys.shape), (2, 3))
                    (logits.flat.square().sum() + keys.square().sum()).backward()
                    self.assertTrue(all(p.grad is None or torch.isfinite(p.grad).all() for p in model.parameters()))
                    model.eval()
                    expected = model(*args, **ctx)
                    path = Path(root) / f"{i}.pth"
                    save_checkpoint(path, model, model.model_config, vocab, GlobalFeatureEncoder())
                    checkpoint, _, _ = load_checkpoint(path)
                    restored = STSValueNetwork(**checkpoint["model_config"]).eval()
                    restored.load_state_dict(checkpoint["model_state_dict"])
                    torch.testing.assert_close(expected, restored(*args, **ctx))

    def test_masked_cells_cannot_influence_compressed_output(self):
        for latents in (16, 32):
            model = small_model(map_latents=latents, pooling="cls_mean").eval()
            args, ctx = synthetic_inputs(model, 2, torch.device("cpu"))
            ctx["map_rooms"][:, 30:] = 0
            first = model(*args, **ctx)
            ctx["map_flags"][:, 30:] = 255
            second = model(*args, **ctx)
            torch.testing.assert_close(first, second)
            ctx["map_rooms"].zero_()
            result = model(*args, **ctx)
            self.assertTrue(torch.isfinite(result.flat).all())
            result.sum().backward()
            self.assertTrue(all(p.grad is None or torch.isfinite(p.grad).all() for p in model.parameters()))

    def test_item_padding_invariance_with_registers_and_mean(self):
        model = small_model(register_tokens=4, pooling="cls_mean").eval()
        args, ctx = synthetic_inputs(model, 2, torch.device("cpu"))
        expected = model(*args, **ctx)
        padded = tuple(torch.cat([t, torch.zeros(2, 5, dtype=t.dtype)], 1) if i < 3 else t
                       for i, t in enumerate(args))
        torch.testing.assert_close(expected, model(*padded, **ctx), atol=1e-6, rtol=1e-5)

    def test_reverse_dag_receives_far_successor_information(self):
        torch.manual_seed(7)
        dag = MapNodeEncoder(16, "dag")
        control = MapNodeEncoder(16, "node_mlp")
        control.load_state_dict(dag.state_dict())
        nodes = torch.randn(1, 105, 16, requires_grad=True)
        edges = torch.full((1, 105), 2, dtype=torch.long)
        edges[:, -7:] = 0
        mask = torch.zeros(1, 105, dtype=torch.bool)
        grad = torch.autograd.grad(dag(nodes, edges, mask)[0, 3, 0], nodes)[0]
        self.assertGreater(grad[0, 14 * 7 + 3].abs().sum().item(), 0)
        changed = nodes.detach().clone()
        changed[0, 14 * 7 + 3, 0] += 3
        self.assertFalse(torch.allclose(dag(nodes, edges, mask)[:, 3], dag(changed, edges, mask)[:, 3]))
        torch.testing.assert_close(control(nodes, edges, mask)[:, 3], control(changed, edges, mask)[:, 3])
        self.assertEqual(sum(p.numel() for p in dag.parameters()), sum(p.numel() for p in control.parameters()))

    def test_different_edges_change_structure_encoding(self):
        model = small_model(map_encoder="dag").eval()
        args, ctx = synthetic_inputs(model, 1, torch.device("cpu"))
        first = model(*args, **ctx)
        ctx["map_outgoing_mask"].zero_()
        self.assertFalse(torch.allclose(first.flat, model(*args, **ctx).flat))

    def test_old_checkpoint_rejected_and_config_mismatch_detected(self):
        model = small_model()
        vocabulary = ItemVocabulary().freeze()
        with self.assertRaisesRegex(ValueError, "disagrees"):
            create_checkpoint(model, {"map_latents": 16}, vocabulary, GlobalFeatureEncoder())
        payload = create_checkpoint(model, model.model_config, vocabulary, GlobalFeatureEncoder())
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "old.pth"
            payload["format_version"] = 10
            torch.save(payload, path)
            with self.assertRaisesRegex(ValueError, "format version 12"):
                load_checkpoint(path)

    def test_cpu_benchmark_labels_device_and_no_gpu_memory(self):
        result = benchmark_model(small_model(), torch.device("cpu"), batches=(1, 8, 32), iterations=2, warmup=0)
        self.assertEqual(result["device"], "cpu")
        self.assertEqual([r["batch"] for r in result["results"]], [1, 8, 32])
        self.assertTrue(all(r["peak_gpu_memory_bytes"] is None for r in result["results"]))

    def test_mechanism_probe_counts_only_observed_items_and_known_keys(self):
        from evaluation_v4 import mechanism_counts
        model = small_model().eval()
        args, ctx = synthetic_inputs(model, 2, torch.device("cpu"))
        args[0].fill_(2)
        args[0][:, 0] = 5  # observed Egg token
        ctx["keys_features"][:, 0] = 1
        ctx["keys_features"][:, 3:5] = 1
        context = (ctx["keys_features"], ctx["map_rooms"], ctx["map_flags"],
                   torch.zeros(2, 8, dtype=torch.long), torch.full((2, 8), 8, dtype=torch.long),
                   ctx["map_outgoing_mask"])
        observed = model(*args, **ctx)
        with torch.no_grad():
            counts, violations = mechanism_counts(
                model, observed, *args, torch.ones(2), context,
                torch.tensor([[1., 0., 1.]] * 2), torch.tensor([[1., 1., 0.]] * 2),
                [5], torch.tensor([True, False]),
            )
        self.assertEqual(counts.tolist(), [2, 0])
        self.assertTrue(np.all(violations <= counts))


class ProtocolTests(unittest.TestCase):
    def test_registry_ablation_experiment(self):
        keys = set(BASELINE) | {"RANDOM_SEED", "EPOCHS", "RUN_FRACTION", "COSINE_DECAY_EPOCHS", "EXPERIMENT_METADATA"}
        old = {key: copy.deepcopy(getattr(Config, key)) for key in keys}
        try:
            baseline = copy.deepcopy(configure_experiment("multiscale", "confirm")["settings"])
            candidate = configure_experiment("multiscale,map_history0", "confirm")["settings"]
            self.assertIs(Config.ABLATE_MAP_HISTORY, True)
            self.assertIs(model_config(32)["ablate_map_history"], True)
            self.assertEqual({key for key in baseline if baseline[key] != candidate[key]},
                             {"ABLATE_MAP_HISTORY"})
            configure_experiment("multiscale")
            self.assertIs(Config.ABLATE_MAP_HISTORY, False)
            self.assertIs(model_config(32)["ablate_map_history"], False)
        finally:
            for key, value in old.items():
                setattr(Config, key, value)

    def test_dataset_subset_keeps_all_run_rows_across_empty_shards(self):
        from dataset import STSDataset, ChunkShuffleSampler
        from test_value_network_v3 import DatasetContextTests
        rows = DatasetContextTests()._rows()
        good = next(str(i) for i in range(10000) if selected_run(str(i), .01))
        bad = next(str(i) for i in range(10000) if not selected_run(str(i), .01))
        with tempfile.TemporaryDirectory() as root:
            first = [dict(rows[0], run_id=bad)]
            second = [dict(rows[0], run_id=good), dict(rows[1], run_id=bad), dict(rows[2], run_id=good)]
            for i, samples in enumerate((first, second)):
                pd.DataFrame(samples).to_parquet(Path(root) / f"train_valid_chunk_{i:05}.parquet", index=False)
            dataset = STSDataset(root, ItemVocabulary().freeze(), GlobalFeatureEncoder(),
                                 split="train", use_map_context=True, max_seq_len=73, run_fraction=.01)
            self.assertEqual(len(dataset), 2)
            self.assertEqual(dataset.identity(0)[0], good)
            self.assertEqual(dataset.identity(1)[0], good)
            self.assertTrue(dataset.identity(1)[1].endswith(":2"))
            self.assertEqual(sorted(ChunkShuffleSampler(dataset)), [0, 1])
            self.assertEqual(len(dataset[0]), 18)

    def test_tiny_training_and_frozen_read_only_evaluation_without_test_shard(self):
        from data_contract import PREPROCESSING_VERSION, FILTER_VERSION, VALUE_TARGET_SCHEMA, HAZARD_ENDPOINTS, TARGET_COLUMNS, MASK_COLUMNS
        from data_pipeline import hazard_targets
        from map_context import MAP_CONTEXT_VERSION
        from evaluation_v4 import evaluate_checkpoint
        from train import train_model
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            data, output = root / "data", root / "training"
            data.mkdir()
            output.mkdir()
            rooms, flags, edges = pack_graph(nodes_as_payload(graph_fixture()), [(2, 0)])
            for split in ("train", "val"):
                rows = []
                for i in range(8):
                    targets, masks = hazard_targets(0, 0 if i % 2 else 57, True, False)
                    rows.append({
                        "run_id": f"{split}-{i // 2}", "split": split, "floor": 0.,
                        "terminal_floor": 0. if i % 2 else 57., "terminal_known": True, "heart_victory": False,
                        "hp": 50., "max_hp": 80., "gold": 99., "ascension": 0.,
                        "ascension_band": 0, "character": "IRONCLAD", "visible_boss": "NO_BOSS",
                        "state_timing": "post_action", "preprocessing_version": PREPROCESSING_VERSION,
                        "filter_version": FILTER_VERSION, "deck": "Strike_R,Bash", "relics": "Molten Egg 2",
                        "act": 1, "map_rooms": rooms, "map_flags": flags, "map_outgoing_mask": edges,
                        "history_rooms": [0] * 8, "key_states": [1, 0, 0], "key_known_mask": [1, 1, 0],
                        **dict(zip(TARGET_COLUMNS, targets)), **dict(zip(MASK_COLUMNS, masks)),
                    })
                pd.DataFrame(rows).to_parquet(data / f"{split}_valid_chunk_00000.parquet", index=False)
            manifest = {
                "preprocessing_version": PREPROCESSING_VERSION, "filter_version": FILTER_VERSION,
                "map_context_version": MAP_CONTEXT_VERSION, "state_timing": "post_action",
                "value_target_schema": VALUE_TARGET_SCHEMA, "hazard_endpoints": list(HAZARD_ENDPOINTS),
                "selection": {}, "content_catalog": {},
                "distributions": {
                    "train_positive_samples_by_target": {"stop_before_03": 4},
                    "train_valid_samples_by_target": {name.removeprefix("valid_"): (8 if j in (0,20) else 4) for j,name in enumerate(MASK_COLUMNS)},
                    "train_valid_samples_by_ascension_band": {"0": 8},
                                  "valid_samples_by_split": {"train": 8, "val": 8}},
            }
            from outcome_statistics import OutcomeAccumulator
            outcomes = OutcomeAccumulator()
            outcomes.add(pd.read_parquet(data / "train_valid_chunk_00000.parquet"))
            manifest["training_outcome_counts"] = outcomes.manifest_counts()
            (data / "dataset_manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            settings = dict(DATA_DIR=str(data), CHECKPOINT_DIR=str(output), D_MODEL=32,
                            N_LAYERS=1, EPOCHS=4, BATCH_SIZE=8, RUN_FRACTION=1.,
                            COSINE_DECAY_EPOCHS=4, EARLY_STOPPING_PATIENCE=0,
                            EXPERIMENT_METADATA={"stage": "pilot", "settings": {"RUN_FRACTION": 1.}})
            with patch.multiple(Config, **settings), patch.dict("os.environ", {"STS_DATALOADER_WORKERS": "0"}):
                train_model()
                source = output / Config.CHECKPOINT_NAME
                before = source.read_bytes()
                # Deliberately incompatible runtime architecture: checkpoint wins.
                with patch.multiple(Config, D_MODEL=256, N_LAYERS=8, MAX_ITEM_TOKENS=1):
                    metrics = evaluate_checkpoint(source, data, root / "evaluation", device_name="cpu")
                self.assertTrue(np.isfinite(metrics["relative_value_nll"]))
                self.assertEqual(set(metrics["common_by_distance"]), {"1-3", "4-8", "9-16", "17+"})
                self.assertEqual(before, source.read_bytes())
                self.assertFalse(load_checkpoint(source)[0]["metadata"]["held_out_test_evaluated"])
                export = root / "evaluation" / "val_predictions.jsonl"
                self.assertTrue(export.exists())
                from evaluation_v4 import _score
                exported = [json.loads(line) for line in export.read_text().splitlines()]
                numerator = np.sum([r["numerator"] for r in exported[1:]], axis=0)
                denominator = np.sum([r["denominator"] for r in exported[1:]], axis=0)
                self.assertAlmostEqual(_score(numerator, denominator, exported[0]["header"]["comparison_contract"]["objective"]), metrics["relative_value_nll"], places=11)

    def test_sampling_is_reproducible_and_clusters_whole_run(self):
        first = [selected_run(str(i), 0.01) for i in range(10000)]
        self.assertEqual(first, [selected_run(str(i), 0.01) for i in range(10000)])
        self.assertTrue(50 < sum(first) < 150)

    def test_registry_defaults_and_pilot_budget(self):
        keys = set(BASELINE) | {"RANDOM_SEED", "EPOCHS", "RUN_FRACTION", "COSINE_DECAY_EPOCHS", "EXPERIMENT_METADATA"}
        old = {key: getattr(Config, key) for key in keys}
        try:
            configure_experiment("dag_latent16")
            self.assertEqual(Config.MAP_LATENTS, 16)
            configure_experiment("baseline")
            self.assertEqual(Config.MAP_LATENTS, 0)
            self.assertEqual(Config.EPOCHS, 8)
            self.assertEqual(Config.RUN_FRACTION, 0.01)
            self.assertEqual(Config.EARLY_STOPPING_PATIENCE, 0)
            configure_experiment("baseline", "confirm", 43)
            self.assertEqual(Config.EPOCHS, 10)
            self.assertEqual(Config.RUN_FRACTION, 1.0)
            self.assertEqual(model_config(32)["item_token_budget"], 73)
            with self.assertRaises(ValueError):
                configure_experiment("gelu,swiglu")
        finally:
            for key, value in old.items():
                setattr(Config, key, value)

    def _exports(self, root, change=-0.1, seeds=(42, 43, 44), fraction=1.0, probes=True):
        paths = [[], []]
        contract = {"dataset_fingerprint": "same", "artifact_fingerprint": "same",
                    "run_fraction": fraction, "split": "val",
                    "objective": {"entropy": [1.] * 5, "heart_weight": 1.0}}
        for side in (0, 1):
            for seed in seeds:
                path = Path(root) / f"{side}-{seed}.jsonl"
                paths[side].append(path)
                with path.open("w", encoding="utf-8") as handle:
                    handle.write(json.dumps({"header": {"seed": seed, "comparison_contract": contract, "completed_epochs": 10, "planned_epochs": 10, "stage": "confirm"}}) + "\n")
                    for i in range(12):
                        for j in range(3):
                            row = {"run_id": str(i), "sample_id": f"{i}:{j}", "label_fingerprint": "same",
                                   "numerator": [0.7 + change * side] * 5, "denominator": [1.] * 5,
                                   "probe_count": int(probes), "probe_violations": 0, "absolute_error": 1., "terminal_count": 1}
                            handle.write(json.dumps(row) + "\n")
        return paths

    def test_paired_bootstrap_whole_runs_and_promotion(self):
        with tempfile.TemporaryDirectory() as root:
            baseline, candidate = self._exports(root)
            result = compare_exports(baseline, candidate, repetitions=100)
            self.assertEqual(result["run_clusters"], 12)
            self.assertAlmostEqual(result["nll_delta"], -0.1)
            self.assertTrue(result["promote"])
            self.assertEqual(result, compare_exports(baseline, candidate, repetitions=100))

    def test_single_seed_ten_epoch_completed_budget_can_promote(self):
        with tempfile.TemporaryDirectory() as root:
            baseline,candidate = self._exports(root,seeds=(42,))
            result = compare_exports(baseline,candidate,repetitions=100)
            self.assertTrue(result["promote"])
            self.assertEqual(result["planned_epochs"],10)
            self.assertEqual(result["seed_count"],1)
            self.assertEqual(result["uncertainty"],"run-cluster-only")

    def test_different_declared_budgets_cannot_compare(self):
        with tempfile.TemporaryDirectory() as root:
            baseline,candidate = self._exports(root,seeds=(42,))
            candidate[0].write_text(candidate[0].read_text().replace('"planned_epochs": 10','"planned_epochs": 11'))
            with self.assertRaisesRegex(ValueError,"different training budgets"):
                compare_exports(baseline,candidate,repetitions=100)

    def test_pilot_and_missing_probes_cannot_promote(self):
        for kwargs in ({"seeds": (42,), "fraction": .01}, {"probes": False}):
            with tempfile.TemporaryDirectory() as root:
                baseline, candidate = self._exports(root, **kwargs)
                self.assertFalse(compare_exports(baseline, candidate, repetitions=100)["promote"])

    def test_mismatched_samples_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            baseline, candidate = self._exports(root)
            data = candidate[0].read_text().replace('"label_fingerprint": "same"', '"label_fingerprint": "different"', 1)
            candidate[0].write_text(data)
            with self.assertRaisesRegex(ValueError, "Paired labels"):
                compare_exports(baseline, candidate, repetitions=100)

    def test_significant_probe_regression_blocks_promotion(self):
        with tempfile.TemporaryDirectory() as root:
            baseline, candidate = self._exports(root)
            for path in candidate:
                data = path.read_text().replace('"probe_violations": 0', '"probe_violations": 1')
                path.write_text(data)
            result = compare_exports(baseline, candidate, repetitions=100)
            self.assertLess(result["nll_delta_ci95"][1], 0)
            self.assertFalse(result["promote"])

    def test_incomplete_training_cannot_promote(self):
        with tempfile.TemporaryDirectory() as root:
            baseline, candidate = self._exports(root)
            for path in candidate:
                path.write_text(path.read_text().replace('"completed_epochs": 10', '"completed_epochs": 8'))
            self.assertFalse(compare_exports(baseline, candidate, repetitions=100)["promote"])

    def test_pilot_selects_one_improved_candidate_per_family(self):
        from evaluation_v4 import select_pilot_candidates
        with tempfile.TemporaryDirectory() as root:
            paths = []
            for name, score in (("baseline", .8), ("gelu", .7), ("swiglu", .6), ("rmsnorm", .9)):
                path = Path(root) / f"{name}.json"
                path.write_text(json.dumps({"header": {"experiments": [name], "stage": "pilot", "seed": 42,
                                                       "completed_epochs": 8, "comparison_contract": {
                                                           "run_fraction": .01, "split": "val"}},
                                             "metrics": {"relative_value_nll": score}}))
                paths.append(path)
            result = select_pilot_candidates(paths)
            self.assertEqual(result["representatives"]["ffn"]["experiment"], "swiglu")
            self.assertEqual(result["representatives"]["normalization"]["experiment"], "baseline")


if __name__ == "__main__":
    unittest.main()
