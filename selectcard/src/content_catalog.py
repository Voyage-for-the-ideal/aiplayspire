import hashlib
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path


_JAVA_ID_PATTERN = re.compile(
    r"public\s+static\s+final\s+String\s+"
    r"(?:ID|CARD_ID|RELIC_ID|POTION_ID)\s*=\s*\"([^\"]+)\""
)
_ENCOUNTER_CONSTANT_PATTERN = re.compile(
    r"(?:public|private)\s+static\s+final\s+String\s+\w+\s*=\s*\"([^\"]+)\""
)
_CASE_PATTERN = re.compile(r"case\s+\"([^\"]+)\"\s*:")
_UPGRADE_PATTERN = re.compile(r"\+\d*$")
_RELIC_ALIASES = {
    # Telemetry uses the display name while the game source uses the internal ID.
    "Snecko Skull": "Snake Skull",
}


def canonical_card_id(value):
    if not isinstance(value, str) or not value:
        raise ValueError("card ID must be a non-empty string")
    return _UPGRADE_PATTERN.sub("", value)


def _extract_ids(paths):
    values = set()
    for path in paths:
        values.update(_JAVA_ID_PATTERN.findall(path.read_text(encoding="utf-8")))
    return values


@dataclass(frozen=True)
class VanillaContentCatalog:
    cards: frozenset
    relics: frozenset
    potions: frozenset
    enemies: frozenset

    @classmethod
    def from_repo(cls, repo_root=None):
        root = Path(repo_root or Path(__file__).resolve().parents[2])
        source_root = root / "cardcrawl"
        monster_helper = source_root / "helpers" / "MonsterHelper.java"
        if not monster_helper.is_file():
            raise FileNotFoundError(
                f"Vanilla content source was not found under {source_root}"
            )

        card_roots = ("blue", "colorless", "curses", "green", "purple", "red")
        cards = set()
        for directory in card_roots:
            cards.update(_extract_ids((source_root / "cards" / directory).glob("*.java")))
        relics = _extract_ids((source_root / "relics").glob("*.java"))
        potions = _extract_ids((source_root / "potions").glob("*.java"))
        helper_text = monster_helper.read_text(encoding="utf-8")
        enemies = set(_ENCOUNTER_CONSTANT_PATTERN.findall(helper_text))
        enemies.update(_CASE_PATTERN.findall(helper_text))

        catalog = cls(
            cards=frozenset(cards),
            relics=frozenset(relics),
            potions=frozenset(potions),
            enemies=frozenset(enemies),
        )
        catalog._validate()
        return catalog

    @classmethod
    def from_payload(cls, payload):
        catalog = cls(
            cards=frozenset(payload["cards"]),
            relics=frozenset(payload["relics"]),
            potions=frozenset(payload["potions"]),
            enemies=frozenset(payload["enemies"]),
        )
        catalog._validate()
        return catalog

    def _validate(self):
        for name in ("cards", "relics", "potions", "enemies"):
            if not getattr(self, name):
                raise ValueError(f"Vanilla {name} catalog is empty")
        overlaps = {
            "cards/relics": self.cards & self.relics,
            "cards/potions": self.cards & self.potions,
            "relics/potions": self.relics & self.potions,
        }
        ambiguous = {name: sorted(values) for name, values in overlaps.items() if values}
        if ambiguous:
            raise ValueError(f"Vanilla item catalogs overlap: {ambiguous}")

    def classify_item(self, value):
        base_id = canonical_card_id(value)
        if base_id in self.cards:
            return "card"
        if self.canonical_relic_id(base_id) in self.relics:
            return "relic"
        if base_id in self.potions:
            return "potion"
        return None

    def canonical_relic_id(self, value):
        if not isinstance(value, str) or not value:
            raise ValueError("relic ID must be a non-empty string")
        return _RELIC_ALIASES.get(value, value)

    def to_payload(self):
        return {
            "cards": sorted(self.cards),
            "relics": sorted(self.relics),
            "potions": sorted(self.potions),
            "enemies": sorted(self.enemies),
        }

    def summary(self):
        payload = self.to_payload()
        aliases = dict(sorted(_RELIC_ALIASES.items()))
        serialized = json.dumps(
            {**payload, "relic_aliases": aliases},
            sort_keys=True,
            separators=(",", ":"),
        )
        return {
            "counts": {name: len(values) for name, values in payload.items()},
            "relic_aliases": aliases,
            "sha256": hashlib.sha256(serialized.encode("utf-8")).hexdigest(),
            "source": os.path.join("cardcrawl", "**", "*.java"),
        }
