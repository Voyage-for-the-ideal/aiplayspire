"""Central policy constants for the deterministic route planner."""

from dataclasses import dataclass


@dataclass(frozen=True)
class RoutePolicyConfig:
    lookahead_depth: int = 6
    discount: float = 0.90

    critical_hp_ratio: float = 0.30
    low_hp_ratio: float = 0.55
    high_hp_ratio: float = 0.80
    gold_low: int = 75
    gold_medium: int = 150
    gold_high: int = 250

    monster_growth: float = 2.0
    elite_growth: float = 6.0
    treasure_value: float = 4.0
    event_value: float = 1.5
    shop_very_low: float = -3.0
    shop_low: float = 1.0
    shop_medium: float = 3.0
    shop_high: float = 5.0

    rest_critical: float = 8.0
    rest_low: float = 5.0
    rest_normal: float = 2.0
    rest_high: float = 0.5
    upgrade_opportunity: float = 2.0

    monster_risk_critical: float = 4.0
    monster_risk_low: float = 2.0
    monster_risk_normal: float = 0.75
    monster_risk_high: float = 0.25
    elite_risk_critical: float = 12.0
    elite_risk_low: float = 7.0
    elite_risk_normal: float = 3.5
    elite_risk_high: float = 1.5
    act_two_elite_multiplier: float = 1.25
    potion_elite_buffer: float = 0.75
    max_buffered_potions: int = 2
    burning_elite_extra_risk: float = 2.0

    sequence_penalty: float = 1.5
    elite_rest_bonus: float = 1.5
    recovery_critical: float = 4.0
    recovery_low: float = 3.0
    recovery_normal: float = 1.5
    recovery_high: float = 0.5
    optionality_per_pivot: float = 1.25
    optionality_cap: float = 3.0
    forced_monster_penalty: float = 1.5
    forced_elite_penalty: float = 4.0
    dead_end_penalty: float = 10.0

    unsafe_elite_horizon: int = 2
    unsafe_combat_streak: int = 3

    def key_bonus(self, key: str, act: int) -> float:
        table = {
            "ruby": (0.25, 0.75, 6.0),
            "sapphire": (0.5, 2.0, 6.0),
            "emerald": (1.0, 3.0, 8.0),
        }
        values = table[key]
        return values[min(max(act, 1), 3) - 1]


# Concise public name used by callers and tests.
RoutePolicy = RoutePolicyConfig
