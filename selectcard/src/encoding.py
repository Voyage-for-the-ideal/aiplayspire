import json
import re
from collections import Counter

try:
    from .data_contract import PREPROCESSING_VERSION
except ImportError:
    from data_contract import PREPROCESSING_VERSION


PAD_TOKEN = "[PAD]"
UNK_TOKEN = "[UNK]"
def parse_item_name(item_name):
    """Return the canonical base name and upgrade level for a card or relic."""
    item_name = str(item_name or "").strip()
    match = re.match(r"^(.*?)(?:\+(\d*))?$", item_name)
    if not match:
        return item_name, 0
    base_name, suffix = match.groups()
    if suffix is None:
        return base_name, 0
    return base_name, int(suffix) if suffix else 1


def split_items(raw_items):
    if raw_items is None:
        return []
    if isinstance(raw_items, str):
        return [item.strip() for item in raw_items.split(",") if item.strip()]
    return [str(item).strip() for item in raw_items if item]


def aggregate_items(deck, relics):
    canonical = [parse_item_name(item) for item in split_items(deck) + split_items(relics)]
    return Counter(item for item in canonical if item[0])


class ItemVocabulary:
    def __init__(self, item2id=None, frozen=False):
        self.item2id = dict(item2id or {PAD_TOKEN: 0, UNK_TOKEN: 1})
        self.frozen = frozen
        self._validate()

    def _validate(self):
        if self.item2id.get(PAD_TOKEN) != 0 or self.item2id.get(UNK_TOKEN) != 1:
            raise ValueError("Vocabulary must reserve ID 0 for [PAD] and ID 1 for [UNK]")
        if len(set(self.item2id.values())) != len(self.item2id):
            raise ValueError("Vocabulary IDs must be unique")

    def add(self, item_name):
        base_name, _ = parse_item_name(item_name)
        if base_name not in self.item2id:
            if self.frozen:
                return self.item2id[UNK_TOKEN]
            self.item2id[base_name] = len(self.item2id)
        return self.item2id[base_name]

    def get_id(self, item_name):
        base_name, _ = parse_item_name(item_name)
        return self.item2id.get(base_name, self.item2id[UNK_TOKEN])

    def freeze(self):
        self.frozen = True
        return self

    def to_dict(self):
        return dict(self.item2id)

    @classmethod
    def from_dict(cls, item2id):
        return cls(item2id=item2id, frozen=True)

    def save(self, filepath):
        with open(filepath, "w", encoding="utf-8") as handle:
            json.dump(self.item2id, handle, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls, filepath):
        with open(filepath, "r", encoding="utf-8") as handle:
            return cls.from_dict(json.load(handle))

    def __len__(self):
        return len(self.item2id)


def encode_items(deck, relics, vocabulary, max_seq_len, max_upgrade, max_count):
    encoded = []
    for (base_name, upgrade), count in aggregate_items(deck, relics).items():
        encoded.append(
            (
                vocabulary.get_id(base_name),
                min(upgrade, max_upgrade - 1),
                min(count, max_count - 1),
            )
        )

    encoded.sort(key=lambda item: (item[0], item[1]))
    encoded = encoded[:max_seq_len]
    pad_length = max_seq_len - len(encoded)
    tokens = [item[0] for item in encoded] + [0] * pad_length
    upgrades = [item[1] for item in encoded] + [0] * pad_length
    counts = [item[2] for item in encoded] + [0] * pad_length
    return tokens, upgrades, counts
