# -*- coding: utf-8 -*-
"""随机遗物池数据：事件 expectimax 打分时枚举 random_relic 的 realization 用。

数据由 cardcrawl/ 反编译源码一次性提取（RelicLibrary 注册调用 + 各遗物构造器的
RelicTier），提取脚本仅用于生成，不在运行时解析 Java。池语义核对结论：
- 随机遗物池只按 tier 划分（RelicLibrary.populateRelicPool = shared 池 + 角色专属池），不分幕。
- 稀有度权重三幕一致：common 50 / uncommon 33 / rare 17（Exordium/TheCity/TheBeyond 的
  initializeLevelSpecificChances）。
- BOSS/SHOP/SPECIAL/STARTER tier 不会出现在 random_relic 的池里，故未收录。
- 近似：不模拟运行时"生成后从池中移除"的变更，只剔除当前已拥有（owned）。
"""

_SHARED_COMMON = [
    "Akabeko", "Anchor", "Ancient Tea Set", "Art of War",
    "Bag of Marbles", "Bag of Preparation", "Blood Vial", "Boot",
    "Bronze Scales", "Centennial Puzzle", "CeramicFish", "Dream Catcher",
    "Happy Flower", "Juzu Bracelet", "Lantern", "MawBank",
    "MealTicket", "Nunchaku", "Oddly Smooth Stone", "Omamori",
    "Orichalcum", "Pen Nib", "Potion Belt", "PreservedInsect",
    "Regal Pillow", "Smiling Mask", "Strawberry", "Tiny Chest",
    "Toy Ornithopter", "Vajra", "War Paint", "Whetstone",
]

_SHARED_UNCOMMON = [
    "Blue Candle", "Bottled Flame", "Bottled Lightning", "Bottled Tornado",
    "Darkstone Periapt", "Eternal Feather", "Frozen Egg 2", "Gremlin Horn",
    "HornCleat", "InkBottle", "Kunai", "Letter Opener",
    "Matryoshka", "Meat on the Bone", "Mercury Hourglass", "Molten Egg 2",
    "Mummified Hand", "Ornamental Fan", "Pantograph", "Pear",
    "Question Card", "Shuriken", "Singing Bowl", "StrikeDummy",
    "Sundial", "The Courier", "Toxic Egg 2", "White Beast Statue",
]

_SHARED_RARE = [
    "Bird Faced Urn", "Calipers", "CaptainsWheel", "Dead Branch",
    "Du-Vu Doll", "FossilizedHelix", "Gambling Chip", "Ginger",
    "Girya", "Ice Cream", "Incense Burner", "Lizard Tail",
    "Mango", "Old Coin", "Peace Pipe", "Pocketwatch",
    "Prayer Wheel", "Shovel", "StoneCalendar", "Thread and Needle",
    "Torii", "TungstenRod", "Turnip", "Unceasing Top",
    "WingedGreaves",
]

_RED_COMMON = [
    "Red Skull",
]

_RED_UNCOMMON = [
    "Paper Frog", "Self Forming Clay",
]

_RED_RARE = [
    "Champion Belt", "Charon's Ashes", "Magic Flower",
]

_GREEN_COMMON = [
    "Snake Skull",
]

_GREEN_UNCOMMON = [
    "Ninja Scroll", "Paper Crane",
]

_GREEN_RARE = [
    "The Specimen", "Tingsha", "Tough Bandages",
]

_BLUE_COMMON = [
    "DataDisk",
]

_BLUE_UNCOMMON = [
    "Cables", "Symbiotic Virus",
]

_BLUE_RARE = [
    "Emotion Chip",
]

_PURPLE_COMMON = [
    "Damaru",
]

_PURPLE_UNCOMMON = [
    "TeardropLocket", "Yang",
]

_PURPLE_RARE = [
    "CloakClasp", "GoldenEye",
]

# returnRandomRelicTier 的稀有度权重（三幕一致）
_COMMON_WEIGHT = 50.0
_UNCOMMON_WEIGHT = 33.0
_RARE_WEIGHT = 17.0

# Cursed Key 开非 Boss 宝箱的诅咒池：CardLibrary.getCurse() 在诅咒全集中均匀抽取，
# 排除 AscendersBane / Necronomicurse / CurseOfTheBell / Pride 后共 10 张（cardRng）。
CHEST_CURSE_POOL = [
    "Clumsy", "Decay", "Doubt", "Injury", "Normality",
    "Pain", "Parasite", "Regret", "Shame", "Writhe",
]

_GROUP_BY_CHARACTER = {
    "IRONCLAD": "_RED_",
    "THE_SILENT": "_GREEN_",
    "DEFECT": "_BLUE_",
    "WATCHER": "_PURPLE_",
}

_BOOK_RELICS = ["Necronomicon", "Enchiridion", "Nilry's Codex"]


def _weighted_tier_pool(group_prefix: str, owned_ids) -> list:
    """按 50/33/17 稀有度权重合并三档 tier 池（shared + 角色专属），剔除已拥有。

    返回 [(relic_id, weight)]，权重和为 1。
    """
    owned = set(owned_ids or ())
    result = []
    for tier, weight in (
        ("COMMON", _COMMON_WEIGHT),
        ("UNCOMMON", _UNCOMMON_WEIGHT),
        ("RARE", _RARE_WEIGHT),
    ):
        pool = globals()[f"_SHARED_{tier}"] + globals()[f"{group_prefix}{tier}"]
        result.extend((relic_id, weight / len(pool)) for relic_id in pool if relic_id not in owned)
    total = sum(weight for _, weight in result)
    if total <= 0:
        return []
    return [(relic_id, weight / total) for relic_id, weight in result]


def relic_pool(pool, character, owned_ids) -> list:
    """展开 EventEffect.random_relic 的 pool 为 [(relic_id, weight)] realization 列表。

    - "ANY" / "FACE"：COMMON+UNCOMMON+RARE（shared + 角色专属），50/33/17 加权，剔除已拥有。
      （FaceTrader 实际取无头遗物分支，分布近似为标准池，误差不影响候选间相对比较。）
    - "RARE"：仅 RARE tier，均匀权重。
    - "BOOK"：Cursed Tome 三本书；已拥有则回退 Circlet（与 Java 侧 cursedTomeBookRelics 一致）。
    - 空/未知池：返回 []，由调用方回退保守处理。
    """
    normalized = (character or "DEFECT").upper()
    prefix = _GROUP_BY_CHARACTER.get(normalized, "_BLUE_")
    owned = set(owned_ids or ())

    if pool in ("ANY", "FACE", None, ""):
        return _weighted_tier_pool(prefix, owned)
    if pool == "RARE":
        candidates = [(relic_id, 1.0) for relic_id in _SHARED_RARE if relic_id not in owned]
        candidates += [(relic_id, 1.0) for relic_id in globals()[f"{prefix}RARE"] if relic_id not in owned]
        return candidates
    if pool == "BOOK":
        available = [relic_id for relic_id in _BOOK_RELICS if relic_id not in owned]
        if not available:
            available = ["Circlet"]
        return [(relic_id, 1.0 / len(available)) for relic_id in available]
    return []


def chest_relic_pool(tier, character, owned_ids) -> list:
    """宝箱开箱遗物（AbstractRoom.addRelicToRewards(tier)）的 realization 池。

    tier 来自 /state 的 chest_relic_tier（"common"/"uncommon"/"rare"），进房时已
    roll 定，故与 random_relic 不同：只在单档池内均匀抽取（shared + 角色专属），
    剔除已拥有。返回 [(relic_id, weight)]，权重和为 1；空/未知档位或池耗尽返回 []。
    """
    normalized_tier = (tier or "").strip().upper()
    if normalized_tier not in ("COMMON", "UNCOMMON", "RARE"):
        return []
    normalized = (character or "DEFECT").upper()
    prefix = _GROUP_BY_CHARACTER.get(normalized, "_BLUE_")
    owned = set(owned_ids or ())
    pool = globals()[f"_SHARED_{normalized_tier}"] + globals()[f"{prefix}{normalized_tier}"]
    available = [relic_id for relic_id in pool if relic_id not in owned]
    if not available:
        return []
    return [(relic_id, 1.0 / len(available)) for relic_id in available]


def potion_pool() -> list:
    """random_potion 的 realization 池。

    注意：价值网络 V(S) 输入不含药水，因此 random_potion 对打分近似无状态变化，
    只计同一选项中的确定性代价（lose_hp/lose_gold）。此函数保留给后续 V 扩展药水
    特征时使用，当前打分路径不调用。
    """
    return []
