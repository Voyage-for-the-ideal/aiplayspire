"""Six-room resource rollout, with explicit uncertainty and no hidden game state."""
from dataclasses import asdict, dataclass, replace
import math

from ..models import visible_boss_of
from .decision import CandidateAssessment, ScoreBreakdown
from .evaluator import LocalRoomParts, RouteEvaluator
from .planner import RoutePlanner
from .strength import clamp, deck_profile


@dataclass(frozen=True)
class ResourcePolicy:
    version: str = "resource-v2.1"
    # A20 hand-set priors in HP, not fitted on evaluation seeds.
    monster_loss: tuple = (8., 12., 14., 16.)
    elite_loss: tuple = (18., 24., 28., 32.)
    boss_reserve: tuple = (24., 32., 36., 45.)
    monster_gold: float = 15.
    elite_gold: float = 30.
    treasure_gold: float = 20.
    prudent_multiplier: float = 1.5
    prudent_margin: float = 2.
    absolute_risk_weight: float = 8.
    ratio_risk_weight: float = 1.5
    shop_spend_cap: float = 150.
    shop_min_spend: float = 50.
    shop_utility_per_gold: float = .035
    heal_utility_per_hp: float = .15
    boss_shortfall_weight: float = .25
    upgrade_gain: float = .08


@dataclass(frozen=True)
class Resources:
    hp: float
    prudent_hp: float
    gold: float
    ruby: float
    emerald: float
    sapphire: float
    upgradable: int
    upgrades: int = 0
    wings: int = 0

    def record(self):
        return {key: round(value, 4) if isinstance(value, float) else value
                for key, value in asdict(self).items()}


def visible_boss(state):
    value = visible_boss_of(state)
    aliases = {"The Champ": "Champ", "Collector": "The Collector",
               "Automaton": "Bronze Automaton", "TimeEater": "Time Eater",
               "AwakenedOne": "Awakened One", "Donu & Deca": "Donu and Deca",
               "CorruptHeart": "Corrupt Heart", "SlimeBoss": "Slime Boss"}
    return aliases.get(value, value)


class ResourceEvaluator(RouteEvaluator):
    def __init__(self, graph, policy, state, resource_policy=None):
        super().__init__(graph, policy)
        self.resources = resource_policy or ResourcePolicy()
        self.profile = deck_profile(state)
        self.boss = visible_boss(state)
        self.relics = {relic.id for relic in state.relics or []}
        self.ascension = max(0, min(20, state.ascension_level or 0))

    def initial(self, context):
        return Resources(float(context.hp), float(context.hp), float(context.gold),
                         float(context.ruby_key), float(context.emerald_key),
                         float(context.sapphire_key), context.upgradable_card_count,
                         wings=context.wing_charges)

    def loss(self, kind, context, resources, burning=False):
        cfg = self.resources
        index = min(4, context.act) - 1
        base = (cfg.elite_loss if kind == "E" else cfg.monster_loss)[index]
        base *= .65 + .35 * self.ascension / 20
        strength = self.profile.strength(kind, upgrades=resources.upgrades, upgrade_gain=cfg.upgrade_gain)
        mean = base / strength * (1.25 if burning else 1)
        return mean, mean * cfg.prudent_multiplier + cfg.prudent_margin

    def _combat(self, symbol, burning, context, current):
        mean, upper = self.loss(symbol, context, current, burning)
        heal = 0
        if "Mark of the Bloom" not in self.relics:
            heal = 12 if "Black Blood" in self.relics else 6 if "Burning Blood" in self.relics else 0
        # Recovery after combat never retroactively makes a lethal fight safe.
        survived = current.prudent_hp - upper > 0
        hp = current.hp - mean
        prudent = current.prudent_hp - upper
        if hp > 0:
            hp = min(context.max_hp, hp + heal)
        if prudent > 0:
            prudent = min(context.max_hp, prudent + heal)
        gold = self.resources.elite_gold if symbol == "E" else self.resources.monster_gold
        if "Ectoplasm" in self.relics:
            gold = 0
        key = 0.
        emerald = current.emerald
        if symbol == "E" and burning:
            key = (1 - emerald) * self.policy.key_bonus("emerald", context.act)
            emerald = 1.
        risk = (self.resources.absolute_risk_weight * upper / max(1., current.hp)
                + self.resources.ratio_risk_weight * max(0., .55 - current.hp / max(1, context.max_hp)))
        if symbol == "E":
            # Preserve the existing aggregate potion buffer, without consumption,
            # identity-based valuation, purchases, or any potion feature changes.
            risk = max(0., risk - min(context.potion_count, self.policy.max_buffered_potions)
                       * self.policy.potion_elite_buffer)
        result = replace(current, hp=hp, prudent_hp=prudent, gold=current.gold + gold, emerald=emerald)
        value = self.policy.elite_growth if symbol == "E" else self.policy.monster_growth
        return [(result, LocalRoomParts(value, key=key, risk=risk), "combat", survived)]

    def transitions(self, symbol, burning, context, current):
        cfg = self.resources
        if symbol in ("M", "E"):
            return self._combat(symbol, burning, context, current)
        if symbol == "R":
            options = []
            if "Coffee Dripper" not in self.relics:
                amount = 0 if "Mark of the Bloom" in self.relics else int(context.max_hp * .3) + (
                    15 if "Regal Pillow" in self.relics else 0)
                healed = max(0., min(amount, context.max_hp - current.hp))
                options.append((replace(current, hp=current.hp + healed,
                    prudent_hp=min(context.max_hp, current.prudent_hp + amount)),
                    LocalRoomParts(healed * cfg.heal_utility_per_hp), "rest", True))
            if current.upgradable > 0 and "Fusion Hammer" not in self.relics:
                options.append((replace(current, upgradable=current.upgradable - 1,
                                        upgrades=current.upgrades + 1),
                                LocalRoomParts(self.policy.upgrade_opportunity), "upgrade", True))
            if current.ruby < 1:
                options.append((replace(current, ruby=1.), LocalRoomParts(
                    key=(1 - current.ruby) * self.policy.key_bonus("ruby", context.act)), "recall", True))
            return options or [(current, LocalRoomParts(), "no_projected_action", True)]
        if symbol == "$":
            spend = min(current.gold, cfg.shop_spend_cap)
            if spend < cfg.shop_min_spend:
                return [(current, LocalRoomParts(self.policy.shop_very_low), "save_gold", True)]
            return [(replace(current, gold=current.gold - spend),
                     LocalRoomParts(spend * cfg.shop_utility_per_gold), "expected_spend", True)]
        if symbol == "T":
            gold = 0 if "Ectoplasm" in self.relics else cfg.treasure_gold
            result = replace(current, gold=current.gold + gold)
            options = [(result, LocalRoomParts(self.policy.treasure_value), "relic_opportunity", True)]
            if current.sapphire < 1:
                options.append((replace(result, sapphire=1.), LocalRoomParts(
                    key=(1 - current.sapphire) * self.policy.key_bonus("sapphire", context.act)), "sapphire", True))
            return options
        if symbol == "?":
            weighted = []
            for name, room in (("monster", "M"), ("elite", "E"), ("shop", "$"), ("treasure", "T"), ("event", "EVENT")):
                probability = context.chance(name)
                if probability <= 0:
                    continue
                outcomes = self.transitions(room, False, context, current)
                # A hidden outcome is averaged, never selected optimistically.
                result = max(outcomes, key=lambda item: item[1].value + item[1].key - item[1].risk)
                weighted.append((probability, result))
            def mean(field):
                return sum(p * getattr(item[0], field) for p, item in weighted)
            result = replace(current, hp=mean("hp"), prudent_hp=mean("prudent_hp"),
                             gold=mean("gold"), emerald=mean("emerald"), sapphire=mean("sapphire"))
            variance = sum(p * (item[0].hp - result.hp) ** 2 for p, item in weighted)
            result = replace(result, prudent_hp=result.prudent_hp - .5 * math.sqrt(variance))
            parts = LocalRoomParts(**{field: sum(p * getattr(item[1], field) for p, item in weighted)
                                      for field in ("value", "key", "risk")})
            return [(result, parts, "weighted_question", result.prudent_hp > 0)]
        value = self.policy.event_value if symbol == "EVENT" else 0.
        return [(current, LocalRoomParts(value), "event_unknown" if symbol == "EVENT" else "pass", True)]

    def local_room_parts(self, symbol, burning, context):
        # Used by the unchanged immediate-only degradation path.
        options = self.transitions((symbol or "?").upper(), burning, context, self.initial(context))
        return max(options, key=lambda item: item[1].value + item[1].key - item[1].risk)[1]

    def _boss_penalty(self, current, context, last_row):
        reserve = self.resources.boss_reserve[min(4, context.act) - 1] / self.profile.strength(
            "B", self.boss, current.upgrades, self.resources.upgrade_gain)
        # Only the first visible Act III boss is used. No second-boss inference.
        if self.boss == "Hexaghost":
            reserve += 2 * int(max(0, current.hp) // 12)
        distance = max(0, 15 - last_row)
        shortfall = max(0., reserve - current.prudent_hp)
        return self.policy.discount ** distance * shortfall * self.resources.boss_shortfall_weight, reserve

    def project_path(self, path, context):
        # Each record owns its state. A dead prefix stays unsafe even if a later
        # camp would heal the arithmetic HP balance back above zero.
        options = [(self.initial(context), ScoreBreakdown(), (), True)]
        for index, node in enumerate(path.nodes):
            following = []
            discount = self.policy.discount ** index
            for current, score, trace, safe in options:
                if index and path.edges[index - 1].winged:
                    current = replace(current, wings=current.wings - 1)
                for after, parts, action, step_safe in self.transitions(
                        (node.symbol or "?").upper(), bool(node.burning), context, current):
                    score_after = replace(score,
                        immediate=score.immediate + (parts.value if index == 0 else 0),
                        future=score.future + (discount * parts.value if index else 0),
                        key=score.key + discount * parts.key,
                        risk=score.risk - discount * parts.risk)
                    record = {"x": node.x, "y": node.y, "symbol": node.symbol,
                              "before": current.record(), "after": after.record(),
                              "action": action, "safe": step_safe,
                              "value": parts.value, "key_value": parts.key, "risk": parts.risk}
                    following.append((after, score_after, trace + (record,), safe and step_safe))
            options = following
        projected = []
        for after, score, trace, safe in options:
            penalty, reserve = self._boss_penalty(after, context, path.nodes[-1].y)
            last = path.nodes[-1]
            dead_end = (-self.policy.dead_end_penalty if last.y < self.graph.max_y
                        and not self.graph.edges_from(last, path.wing_charges_remaining) else 0.)
            projected.append((after, replace(score, risk=score.risk - penalty, dead_end=dead_end),
                              trace, safe, reserve))
        return max(projected, key=lambda item: (item[3], item[1].total if item[3]
                   else item[0].prudent_hp, item[1].total))

    def evaluate_candidate(self, node, context, choice_index):
        paths = self.graph.paths_from(node, self.policy.lookahead_depth - 1, context.wing_charges)
        projections = [(path, self.project_path(path, context)) for path in paths]
        path, (after, score, trace, safe, reserve) = max(projections,
            key=lambda pair: (pair[1][3], pair[1][1].total if pair[1][3]
                              else pair[1][0].prudent_hp, pair[1][1].total))
        features = self.features.extract(node, context)
        score = replace(score, optionality=min(self.policy.optionality_cap,
            features.optionality_pivots * self.policy.optionality_per_pivot))
        reasons = () if safe else ("projected_hp_exhausted",)
        if reserve > after.prudent_hp:
            reasons += ("boss_reserve_shortfall",)
        diagnostics = {"version": self.resources.version, "profile": self.profile.as_dict(),
                       "visible_boss": self.boss, "boss_reserve": reserve,
                       "trajectory": trace, "terminal": after.record(),
                       "uncertainty": "hand_set_expected_and_prudent_not_probability"}
        return CandidateAssessment(
            choice_index=choice_index, x=node.x, y=node.y, symbol=(node.symbol or "?").upper(),
            score=score.total, breakdown=score, safe=safe,
            risk_rank=(-after.prudent_hp if not safe else -score.risk), reasons=reasons,
            best_path=tuple((item.x, item.y) for item in path.nodes),
            reaches_rest=features.reaches_rest, reaches_burning_elite=features.reaches_burning_elite,
            diagnostics=diagnostics), features


class ResourceRoutePlanner(RoutePlanner):
    def _make_evaluator(self, graph, state):
        return ResourceEvaluator(graph, self.policy, state)
