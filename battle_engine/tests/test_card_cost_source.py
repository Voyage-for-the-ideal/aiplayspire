"""Check native card construction and upgrade costs against the local game source."""
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class CardCostSourceTests(unittest.TestCase):
    def test_native_cost_table_matches_all_mapped_vanilla_cards(self):
        header = (ROOT / "third_party/sts_lightspeed/include/constants/Cards.h").read_text(encoding="utf-8")
        enum_body = re.search(r"enum class CardId\s*:\s*std::uint16_t\s*\{(.*?)\};", header, re.S).group(1)
        enums = [entry.strip().split("=")[0].strip() for entry in enum_body.split(",") if entry.strip()]
        ids_body = re.search(r"cardStringIds\[\]\s*=\s*\{(.*?)\};", header, re.S).group(1)
        ids = json.loads("[" + ids_body.rstrip(", ") + "]")
        self.assertEqual(len(enums), len(ids))
        enum_by_id = dict(zip(ids, enums))

        switch = header.split("static constexpr int getEnergyCost(")[1].split("\n    }")[0]
        native = {}
        for cases, expression in re.findall(r"((?:\s*case CardId::\w+:\s*)+)\s*return\s+([^;]+);", switch):
            choice = re.fullmatch(r"upgraded\s*\?\s*(-?\d+)\s*:\s*(-?\d+)", expression.strip())
            costs = (int(choice.group(2)), int(choice.group(1))) if choice else (int(expression), int(expression))
            for name in re.findall(r"CardId::(\w+)", cases):
                self.assertNotIn(name, native)
                native[name] = costs

        unsupported = {"Impulse", "Calm", "Wrath", "Discipline", "Unraveling"}
        compared = 0
        for path in (ROOT / "cardcrawl/cards").rglob("*.java"):
            if "deprecated" in path.parts:
                continue
            source = path.read_text(encoding="utf-8")
            if "extends AbstractCard" not in source:
                continue
            constructor = re.search(
                r'super\(\s*"([^"]+)"\s*,\s*cardStrings\.NAME\s*,\s*"[^"]+"\s*,\s*(-?\d+)\s*,',
                source, re.S)
            self.assertIsNotNone(constructor, str(path))
            card_id, cost = constructor.group(1), int(constructor.group(2))
            if card_id not in enum_by_id:
                self.assertIn(card_id, unsupported)
                continue
            # Blood for Blood has a separate dynamic-cost implementation;
            # its literal branch still gives the initial upgraded base cost.
            upgrades = re.findall(r"upgradeBaseCost\(\s*(\d+)\s*\)", source)
            upgraded = int(upgrades[-1]) if upgrades else cost
            with self.subTest(card=card_id):
                self.assertEqual(native.get(enum_by_id[card_id], (1, 1)), (cost, upgraded))
            compared += 1
        self.assertGreaterEqual(compared, 350)
