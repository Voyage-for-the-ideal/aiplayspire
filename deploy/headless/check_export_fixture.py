"""Check Java-produced local-object fixture through the actual Python mapper."""
import argparse
import hashlib
import json
from pathlib import Path

from sts_ai_framework.headless.state import HeadlessStateMapper
from sts_ai_framework.models import GameState


def check(source: Path, output: Path):
    raw = json.loads(source.read_text(encoding="utf-8"))
    mapper = HeadlessStateMapper()
    state = mapper.map_state(raw, run_id="synthetic-java-objects", sequence=0)
    assert [card.reference for card in state.deck] == ["Searing Blow+3", "Searing Blow"]
    assert state.deck[0].uuid == "00000000-0000-4000-8000-000000000001"
    assert state.deck[0].uuid == state.hand[0].uuid
    assert state.deck[0].uuid != state.deck[1].uuid
    assert state.deck[0].base_damage == 27 and state.deck[0].damage == 99
    assert state.deck[0].cost == 2 and state.deck[0].cost_for_turn == 0
    assert [(node.x, node.y) for node in state.map_nodes] == [(2, 0), (3, 1), (3, 2)]
    assert state.map_nodes[1].burning
    assert state.current_map_node.x == 2 and state.current_map_node.y == 0
    assert state.keys.emerald and state.keys.model_fields_set == {"ruby", "emerald", "sapphire"}
    payload = mapper.map_payload(raw, run_id="synthetic-java-objects", sequence=0)
    assert GameState(**json.loads(json.dumps(payload))) == state
    assert not state.choice_list and not state.can_proceed and not state.can_cancel
    report = {"status": "passed", "scope": "synthetic-card-and-map-objects-no-run",
              "input_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "deck_references": [card.reference for card in state.deck], "state": payload}
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("JAVA_TO_GAMESTATE_CONTRACT_OK")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    check(args.fixture, args.output)
