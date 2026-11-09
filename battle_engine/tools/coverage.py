"""Audit explicit card dispatch, not simulation accuracy or real-engine parity."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
VENDOR = ROOT / "third_party/sts_lightspeed"


def inventory():
    constants = (VENDOR / "include/constants/Cards.h").read_text(encoding="utf-8")
    def array(name):
        body = re.search(rf"\b{name}\[\]\s*(?:=\s*)?\{{(.*?)\}};", constants, re.S).group(1)
        return body
    enums = re.findall(r'"([A-Z_0-9]+)"', array("cardEnumStrings"))
    ids = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', array("cardStringIds"))
    colors = re.findall(r"CardColor::([A-Z]+)", array("cardColors"))
    assert len(enums) == len(ids) == len(colors)
    function = constants.split("static inline CardColor getCardColor", 1)[1].split("return cardColors", 1)[0]
    overrides = {}
    for cases, color in re.findall(r"((?:\s*case CardId::\w+:)+)\s*return CardColor::(\w+)", function):
        for name in re.findall(r"CardId::(\w+)", cases):
            overrides[name] = color
    colors = [overrides.get(e, c) for e, c in zip(enums, colors)]
    core = (VENDOR / "src/combat/BattleContext.cpp").read_text(encoding="utf-8")
    owned = (VENDOR / "src/combat/CharacterCards.cpp").read_text(encoding="utf-8")
    catalog = re.search(r"constexpr CardId supported\[\]\s*=\s*\{(.*?)\};", owned, re.S)
    runtime = set(re.findall(r"CardId::([A-Z_0-9]+)", catalog.group(1)))
    dispatch = set()
    for method, end in (("useAttackCard", "useSkillCard"), ("useSkillCard", "usePowerCard"), ("usePowerCard", "onUseAttackCard")):
        body = core.split(f"void BattleContext::{method}()", 1)[1].split(f"void BattleContext::{end}()", 1)[0]
        dispatch.update(re.findall(r"case CardId::([A-Z_0-9]+)", body))
    dispatch.update(re.findall(r"case CardId::([A-Z_0-9]+)", owned))
    roles = {}
    audit = []
    source_cards = {}
    for folder in ("green", "purple"):
        for path in (ROOT / "cardcrawl/cards" / folder).glob("*.java"):
            source = path.read_text(encoding="utf-8")
            card_id = re.search(r'public static final String ID\s*=\s*"([^"]+)"', source)
            if card_id:
                source_cards[card_id.group(1)] = (path, source)
    for role, color in (("IRONCLAD", "RED"), ("THE_SILENT", "GREEN"), ("DEFECT", "BLUE"), ("WATCHER", "PURPLE")):
        cards = [(e, i) for e, i, c in zip(enums, ids, colors) if c == color]
        missing = [i for e, i in cards if e not in dispatch]
        gated = [i for e, i in cards if color in ("GREEN", "PURPLE") and e not in runtime]
        roles[role] = dict(total=len(cards), explicit_dispatch=len(cards) - len(missing), missing=missing,
                           runtime_supported=len(cards)-len(gated), runtime_missing=gated)
        if color not in ("GREEN", "PURPLE"):
            continue
        for enum, card_id in cards:
            path, source = source_cards.get(card_id, (None, ""))
            audit.append(dict(role=role, id=card_id, enum=enum,
                source=str(path.relative_to(ROOT)) if path else None,
                dispatch=enum in dispatch, runtime_gate=enum in runtime,
                actions=sorted(set(re.findall(r"new (\w+Action)\(", source))),
                powers=sorted(set(re.findall(r"new (\w+Power)\(", source))),
                upgrade_operations=re.findall(r"\b(upgrade(?:Damage|Block|MagicNumber|BaseCost))\(([^)]+)\)", source),
                verification="dispatch-only; effect tests and live replay are separate gates"))
    return dict(roles=roles, complete_dispatch=all(not r["missing"] for r in roles.values()),
                complete_runtime=all(not r["runtime_missing"] for r in roles.values()),
                catalog_without_dispatch=sorted(runtime-dispatch), cards=audit,
                real_engine_checked=False, meaning="Dispatch coverage only; interaction and replay tests are separate gates")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", type=Path)
    p.add_argument("--require-complete", action="store_true")
    args = p.parse_args()
    report = inventory()
    text = json.dumps(report, ensure_ascii=False, indent=2)
    print(text)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    return int(args.require_complete and (not report["complete_dispatch"] or not report["complete_runtime"]
                                          or bool(report["catalog_without_dispatch"])))


if __name__ == "__main__":
    raise SystemExit(main())
