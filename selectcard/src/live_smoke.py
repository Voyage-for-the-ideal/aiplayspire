"""Synthetic logs -> export -> CPU updates -> reload. Never contacts a game."""
import argparse
import json
from pathlib import Path

from selectcard.policy.contracts import split_for
from sts_ai_framework.learning_trace import LearningRecorder
from sts_ai_framework.llm_agent_parts.value_payload import value_state_payload
from sts_ai_framework.models import ActionType, GameAction, GameState
from .live_value_data import export_live


def fixture_state(**updates):
    card = dict(index=0, choice_index=0, uuid="strike", id="Strike_R", name="Strike", cost=1,
                cost_for_turn=1, type="ATTACK", base_damage=6)
    values = dict(character="IRONCLAD", ascension_level=20, seed_group="fixture", act=1, floor=5,
        player=dict(current_hp=40, max_hp=80, energy=3, gold=50, block=0), room_phase="COMPLETE",
        screen_type="CARD_REWARD", choice_list=["Strike"], card_reward_cards=[card],
        deck=[card], relics=[dict(id="Burning Blood", name="Burning Blood", counter=-1)],
        keys=dict(ruby=False, emerald=False, sapphire=False),
        run_mode=dict(is_daily=False, is_endless=False, is_trial=False, is_beta=False, chose_seed=False, special_seed=0),
        map_nodes=[dict(x=0, y=4, symbol="M", children=[dict(x=0, y=5)]),
                   dict(x=0, y=5, symbol="M", children=[dict(x=0, y=6)]),
                   dict(x=0, y=6, symbol="E", children=[])],
        current_map_node=dict(x=0, y=4, symbol="M"),
        current_map_choices=[dict(choice_index=0, x=0, y=5, symbol="M", burning=False, winged=False)])
    values.update(updates)
    return GameState(**values)


def fixture_seed(split, index=0):
    for n in range(10000):
        value = f"synthetic-{split}-{index}-{n}"
        if split_for(value) == split:
            return value
    raise AssertionError("fixture seed search exhausted")


def record_fixture(recorder, seed_group, *, died=False, censored=False, heart=False):
    before = fixture_state(seed_group=seed_group)
    recorder.observe(before, 0)
    after = before.model_copy(deep=True)
    after.deck.append(before.deck[0].model_copy(update={"index": 1, "uuid": "second"}))
    after.screen_type = "MAP"
    after.choice_list = ["M"]
    # The unselected hypothetical upgrade must not acquire this run's label.
    rejected = value_state_payload(after)
    rejected["deck"] = ["Strike_R+1"]
    recorder.value_query(rejected, dict(expected_floor=20., heart_probability=.1, value=.34))
    recorder.value_query(value_state_payload(after), dict(expected_floor=18., heart_probability=.05, value=.3))
    action = GameAction(type=ActionType.CHOOSE, choice_index=0)
    recorder.begin(before, action, 1, behavior={"source": "value"})
    recorder.observe(before, 2)  # An ACK/wait produces no confirmed sample.
    recorder.observe(after, 3)
    recorder.begin(after, action, 4, behavior={"source": "map"})
    fighting = after.model_copy(deep=True)
    fighting.floor = 6
    fighting.current_map_node = after.current_map_node.model_copy(update={"y": 5})
    fighting.screen_type, fighting.room_phase = "NONE", "COMBAT"
    recorder.observe(fighting, 5)
    end = fighting.model_copy(deep=True)
    end.room_phase = "COMPLETE"
    end.player.current_hp = 0 if died else 45  # Net recovery is a signed loss of -5.
    if died:
        end.screen_type, end.game_over_reason, end.seed_group = "GAME_OVER", "defeat", None
        recorder.observe(end, 6)
        return
    end.screen_type = "COMBAT_REWARD"
    recorder.observe(end, 6)
    end.screen_type = "MAP"
    recorder.observe(end, 7)
    if censored:
        recorder.close()
    else:
        end.screen_type = "GAME_OVER"
        end.game_over_reason = "victory" if heart else "defeat"
        end.act, end.floor = (4, 56) if heart else (1, 14)
        end.seed_group = None
        recorder.observe(end, 8)


def synthetic_pipeline(output_dir):
    import torch
    from .checkpointing import save_checkpoint
    from .dataset import build_training_artifacts
    from .finetune import cpu_smoke
    from .model import STSValueNetwork
    from selectcard.routing_calibration import cpu_smoke as calibration_smoke

    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=False)
    recorder = LearningRecorder(output / "traces", metadata={"synthetic": True, "runtime": {"backend": "fixture"}})
    try:
        for split in ("train", "val", "test"):
            for index in range(2):
                record_fixture(recorder, fixture_seed(split, index), died=bool(index))
    finally:
        recorder.close()
    export = export_live(output / "traces", output / "data", chunk_size=2)
    vocabulary, encoder, statistics = build_training_artifacts(output / "data", include_baselines=True, use_cache=False)
    torch.manual_seed(42)
    model = STSValueNetwork(len(vocabulary), d_model=32, n_layers=2, n_heads=4,
                            use_map_context=True, dropout=0.)
    parent = output / "synthetic-parent.pth"
    save_checkpoint(parent, model, model.model_config, vocabulary, encoder,
        {"baseline_statistics": statistics, "dataset": {"difficulty_weights": [0.] * 5 + [1.]},
         "synthetic": True, "seed": 42})
    value = cpu_smoke(output / "data", parent, output / "value", steps=2)
    routing = calibration_smoke(output / "data" / "rooms.jsonl", output / "routing", steps=20)
    report = dict(synthetic_only=True, device="cpu", export=export, value=value, routing=routing,
                  real_games_started=0, gpu_jobs_started=0, promotable=False)
    (output / "report.json").write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args(argv)
    print(json.dumps(synthetic_pipeline(args.output_dir)))


if __name__ == "__main__":
    main()
