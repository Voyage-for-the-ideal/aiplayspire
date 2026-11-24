"""Visible-deck rule estimates, not a learned win probability or a combat simulator.

Scores are energy-adjusted per-card densities. Bad cards remain in the denominator.
No rarity, outcome, seed, future reward, potion, or model prediction is consumed.
"""
from dataclasses import asdict, dataclass
import math


def clamp(value, low, high):
    return max(low, min(high, value))


# IDs, not localized descriptions. Magnitudes use the actual upgraded instance.
DRAW = {"Pommel Strike": 1, "Shrug It Off": 1, "Battle Trance": 3,
        "Offering": 3, "Burning Pact": 2, "Dark Embrace": 1,
        "Backflip": 2, "Acrobatics": 3, "Prepared": 1, "Calculated Gamble": 3,
        "Coolheaded": 1, "Compile Driver": 2, "Skim": 3, "Reboot": 3,
        "Scrawl": 4, "Empty Mind": 2, "CutThroughFate": 1, "Rushdown": 1}
ENERGY = {"Offering": 2, "Seeing Red": 2, "Bloodletting": 2,
          "Adrenaline": 1, "Tactician": 1, "Concentrate": 1,
          "Turbo": 2, "Double Energy": 2, "Aggregate": 2,
          "DevaForm": 2, "Collect": 1}
POISON = {"Deadly Poison": 5, "Bouncing Flask": 9, "Poisoned Stab": 3,
          "Noxious Fumes": 2, "Crippling Poison": 4, "Corpse Explosion": 6,
          "Envenom": 1}
ORB = {"Zap": 3, "Ball Lightning": 3, "Electrodynamics": 6, "Chaos": 3,
       "Cold Snap": 0, "Coolheaded": 0, "Glacier": 0, "Doom and Gloom": 5,
       "Darkness": 5, "Rainbow": 5, "Tempest": 4, "Chill": 0}
STRENGTH = {"Inflame": 2, "Demon Form": 3, "Spot Weakness": 3, "Rupture": 1}
BLOCK_SCALE = {"Footwork": 2, "Talk to the Hand": 2, "MentalFortress": 4,
               "Like Water": 5, "Loop": 2, "Consume": 2}
MULTI = {"Twin Strike": 2, "Pummel": 4, "Sword Boomerang": 3,
         "Flying Knee": 1, "Dagger Spray": 2, "Riddle With Holes": 5,
         "Rip and Tear": 2, "Barrage": 2, "BowlingBash": 2, "Tantrum": 3,
         "Ragnarok": 5}
AOE = {"Cleave", "Whirlwind", "Immolate", "Thunderclap", "Dagger Spray",
       "All Out Attack", "Die Die Die", "Corpse Explosion", "Doom and Gloom",
       "Electrodynamics", "Sweeping Beam", "Hyperbeam", "Consecrate",
       "Conclude"}


@dataclass(frozen=True)
class DeckProfile:
    frontload: float = 1.0
    defense: float = 1.0
    aoe: float = 0.0
    scaling: float = 0.0
    cycle: float = 1.0
    power_dependence: float = 0.0
    coverage: float = 0.0
    unknown_ids: tuple = ()
    deck_size: int = 0

    def as_dict(self):
        return asdict(self)

    def strength(self, kind="M", boss="", upgrades=0, upgrade_gain=.08):
        damage, block, aoe, scaling, cycle = .50, .35, .15, .20, .15
        if kind == "E":
            aoe, scaling = .18, .12
        if boss == "Champ":
            scaling, damage = .50, .65
        elif boss in ("The Collector", "Donu and Deca"):
            aoe, scaling = .50, .30
        elif boss in ("Bronze Automaton", "The Guardian", "Corrupt Heart"):
            block, scaling = .60, .30
        elif boss == "Slime Boss":
            damage, aoe = .80, .25
        elif boss == "Hexaghost":
            block, damage = .45, .60
        elif boss == "Time Eater":
            damage, cycle = .70, .03
        elif boss == "Awakened One":
            scaling *= max(.35, 1 - self.power_dependence)
        numerator = (damage * self.frontload + block * self.defense
                     + cycle * self.cycle + aoe * self.aoe + scaling * self.scaling)
        return clamp(numerator / (damage + block + cycle) + upgrade_gain * upgrades, .45, 2.5)


def deck_profile(state):
    deck = state.deck or []
    if not deck:
        return DeckProfile(unknown_ids=("missing_deck",))
    n = len(deck)
    ids = {card.id for card in deck}
    relics = {relic.id for relic in state.relics or []}
    has_poison = bool(ids & POISON.keys())
    has_orbs = bool(ids & ORB.keys()) or bool(relics & {"Cracked Core", "Frozen Core"})
    has_frost = bool(ids & {"Cold Snap", "Coolheaded", "Glacier", "Chill", "Rainbow"})
    attacks = sum(card.type == "ATTACK" for card in deck)
    skills = sum(card.type == "SKILL" for card in deck)
    exhaust_engine = bool(ids & {"Corruption", "Burning Pact", "True Grit", "Second Wind", "Fiend Fire"})
    discard_engine = bool(ids & {"Acrobatics", "Prepared", "Dagger Throw", "Survivor", "Calculated Gamble"})
    stance_engine = bool(ids & {"Eruption", "Tantrum", "Crescendo"}) and bool(
        ids & {"Vigilance", "EmptyBody", "EmptyFist", "FearNoEvil", "InnerPeace"})
    damage = block = aoe = scaling = draw = energy = powers = 0.0
    covered = 0
    unknown = set()
    for card in deck:
        if card.type in ("CURSE", "STATUS"):
            covered += 1
            continue
        cid = card.id
        efficiency = 1 / math.sqrt(max(.5, card.cost if card.cost >= 0 else 2))
        d = card.base_damage if card.base_damage is not None else card.damage
        b = card.base_block if card.base_block is not None else card.block
        magic = card.base_magic_number if card.base_magic_number is not None else card.magic_number
        recognized = d is not None or b is not None
        # Older bridges omit the numerics: use a neutral generic estimate, not zero.
        d = max(0, d) if d is not None else (6 if card.type == "ATTACK" else 0)
        b = max(0, b) if b is not None else 0
        if cid in MULTI:
            d *= max(1, magic or (MULTI[cid] + int(card.upgrades > 0 and cid in {"Pummel", "Sword Boomerang", "Tantrum", "Ragnarok"})))
            recognized = True
        if cid == "Body Slam":
            d = 8 if any((c.block or 0) > 0 for c in deck) else 0
            recognized = True
        if cid in POISON:
            d += max(1, magic or POISON[cid]) * 1.5
            scaling += .3 * efficiency
            recognized = True
        if cid == "Catalyst":
            scaling += (1 + .5 * card.upgrades) if has_poison else 0
            recognized = True
        if cid in ORB:
            d += ORB[cid]
            b += 4 if cid in {"Cold Snap", "Coolheaded", "Glacier", "Chill", "Rainbow"} else 0
            recognized = True
        if cid == "Dualcast":
            d += 12 if has_orbs else 0
            recognized = True
        if cid in {"Defragment", "Biased Cognition", "Capacitor"}:
            scaling += max(1, magic or (1 + card.upgrades)) * .45 if has_orbs else 0
            b += 3 if has_frost else 0
            recognized = True
        if cid in STRENGTH:
            active = (cid != "Rupture" or bool(ids & {"Offering", "Hemokinesis", "Bloodletting", "Combust", "Brutality", "Pain"})
                      or ("Blue Candle" in relics and any(c.type == "CURSE" for c in deck)))
            scaling += max(1, magic or STRENGTH[cid]) * min(1, attacks / 5) * .25 if active else 0
            recognized = True
        if cid in BLOCK_SCALE:
            active = cid not in {"Loop", "Consume"} or has_orbs
            if cid == "MentalFortress":
                active = stance_engine
            elif cid == "Like Water":
                active = bool(ids & {"Vigilance", "InnerPeace", "Meditate", "FearNoEvil", "Tranquility"})
            elif cid == "Talk to the Hand":
                active = attacks > 0
            scaling += BLOCK_SCALE[cid] * .18 if active else 0
            recognized = True
        if cid in {"Corruption", "Dark Embrace", "Feel No Pain"}:
            scaling += min(1.5, skills / 6) if exhaust_engine and skills else 0
            recognized = True
        if cid == "Barricade":
            scaling += .7 if any((c.block or 0) >= 8 for c in deck) else 0
            recognized = True
        if cid in {"Accuracy", "Infinite Blades"}:
            scaling += .7 if ids & {"Blade Dance", "Cloak And Dagger", "Infinite Blades"} else 0
            recognized = True
        if cid in {"Eruption", "Tantrum", "Rushdown", "MentalFortress", "Talk to the Hand"}:
            scaling += .7 if stance_engine else 0
            recognized = True
        card_draw, card_energy = DRAW.get(cid, 0), ENERGY.get(cid, 0)
        if cid == "Dark Embrace" and not exhaust_engine:
            card_draw = 0
        if cid == "Rushdown" and not stance_engine:
            card_draw = 0
        if cid in {"Tactician", "Concentrate"} and not discard_engine:
            card_energy = 0
        if cid in DRAW or cid in ENERGY:
            draw += card_draw * efficiency
            energy += card_energy * efficiency
            recognized = True
        damage += d * efficiency
        block += b * efficiency
        if card.target == "ALL_ENEMY" or cid in AOE:
            aoe += d * efficiency
        powers += card.type == "POWER"
        if recognized:
            covered += 1
        else:
            unknown.add(cid)
    if "Vajra" in relics:
        damage += attacks
    if "Oddly Smooth Stone" in relics:
        block += sum((card.block or 0) > 0 for card in deck)
    if "DataDisk" in relics and has_orbs:
        damage += 3
        block += 3 if has_frost else 0
    if "Dead Branch" in relics and exhaust_engine:
        scaling += 1
    if "Snecko Eye" in relics:
        draw += 2
    return DeckProfile(
        frontload=clamp(damage / n / 3.0, .2, 4),
        defense=clamp(block / n / 2.5, .15, 4),
        aoe=clamp(aoe / n / 3, 0, 3), scaling=clamp(scaling * 5 / n, 0, 3),
        cycle=clamp(1 + (draw + 1.5 * energy) / n - max(0, n - 20) * .015, .5, 3),
        power_dependence=powers / n, coverage=covered / n,
        unknown_ids=tuple(sorted(unknown)), deck_size=n)
