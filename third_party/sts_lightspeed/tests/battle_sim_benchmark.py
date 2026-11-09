#!/usr/bin/env python3
import argparse
import json
import subprocess
from pathlib import Path


def run(command: list[str], timeout: int) -> dict:
    result = subprocess.run(
        command, check=True, capture_output=True, text=True, timeout=timeout
    )
    return json.loads(result.stdout)


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Compare every first action by replanning to combat end."
    )
    parser.add_argument(
        "fixture",
        nargs="?",
        type=Path,
        default=root / "tests/fixtures/defectxx11_bronze_automaton_turn2.json",
    )
    parser.add_argument("--battle-sim", type=Path, default=root / "build/battle-sim")
    parser.add_argument(
        "--battle-agent", type=Path, default=root / "build/battle-agent"
    )
    parser.add_argument("--simulations", type=int, default=20_000)
    parser.add_argument("--threads", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    budget = [str(args.simulations), str(args.threads)]
    root_result = run(
        [str(args.battle_sim), str(args.fixture), *budget, "10000"],
        args.timeout,
    )
    print(
        f"Fixture: {args.fixture.name}\n"
        f"Root choice: {root_result['rootCommand']} "
        f"[{root_result['rootSelectionPolicy']}]\n"
    )
    print("first action | outcome | final HP | turns")
    print("-------------|---------|----------|------")
    selected_outcome = None
    has_winning_action = False
    for candidate in root_result["rootActions"]:
        result = run(
            [
                str(args.battle_agent),
                str(args.fixture),
                *budget,
                str(candidate["edgeIndex"]),
            ],
            args.timeout,
        )
        if not result["actions"] or result["actions"][0] != candidate["action"]:
            raise RuntimeError(
                f"forced edge {candidate['edgeIndex']} produced a different action"
            )
        outcome = {0: "unfinished", 1: "win", 2: "loss"}.get(
            result["outcome"], str(result["outcome"])
        )
        has_winning_action |= result["outcome"] == 1
        marker = "*" if candidate["action"] == root_result["rootCommand"] else " "
        if marker == "*":
            selected_outcome = result["outcome"]
        print(
            f"{marker} {candidate['action']:<10} | {outcome:<7} | "
            f"{result['playerCurHp']:>8} | {result['turn']:>5}"
        )
    if has_winning_action and selected_outcome != 1:
        raise RuntimeError("selected action loses while another first action wins")


if __name__ == "__main__":
    main()
