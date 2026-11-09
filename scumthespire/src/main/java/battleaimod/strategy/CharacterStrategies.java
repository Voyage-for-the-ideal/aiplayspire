package battleaimod.strategy;

import battleaimod.battleai.playorder.DefectPlayOrder;
import battleaimod.battleai.playorder.IronCladPlayOrder;
import battleaimod.battleai.playorder.SilentPlayOrder;
import battleaimod.evaluation.CreaturePowerUtils;
import battleaimod.evaluation.ThreatEvaluator;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.blue.*;
import com.megacrit.cardcrawl.cards.green.*;
import com.megacrit.cardcrawl.cards.purple.*;
import com.megacrit.cardcrawl.cards.red.*;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass;
import savestate.monsters.MonsterState;
import savestate.orbs.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Main class selects fallback ranks; actual powers/cards activate mechanics across colors. */
public final class CharacterStrategies {
    private CharacterStrategies() {}
    private static final EnumMap<PlayerClass, CharacterStrategy> REGISTRY = new EnumMap<>(PlayerClass.class);
    private static final Set<String> EXITS = new HashSet<>(Arrays.asList(
            EmptyFist.ID, EmptyBody.ID, EmptyMind.ID, Vigilance.ID, FearNoEvil.ID,
            InnerPeace.ID, Tranquility.ID, Meditate.ID));
    static {
        REGISTRY.put(PlayerClass.IRONCLAD, new Ironclad());
        REGISTRY.put(PlayerClass.THE_SILENT, new Silent());
        REGISTRY.put(PlayerClass.DEFECT, new Defect());
        REGISTRY.put(PlayerClass.WATCHER, new Watcher());
    }
    public static boolean isStanceExit(String id) { return EXITS.contains(id); }
    public static CharacterStrategy forClass(PlayerClass role) {
        CharacterStrategy strategy = REGISTRY.get(role);
        return strategy == null ? GENERIC : strategy;
    }
    public static CharacterStrategy forCard(StrategyContext c, StrategyContext.Card card) {
        if (card.color != null) {
            switch (card.color) {
                case RED: return REGISTRY.get(PlayerClass.IRONCLAD);
                case GREEN: return REGISTRY.get(PlayerClass.THE_SILENT);
                case BLUE: return REGISTRY.get(PlayerClass.DEFECT);
                case PURPLE: return REGISTRY.get(PlayerClass.WATCHER);
                default: break;
            }
        }
        return forClass(c.state.playerState.chosenClass);
    }
    public static MechanismScore evaluate(StrategyContext c) {
        MechanismScore score = new MechanismScore();
        if (c.features.allEnemiesDead || c.features.playerCurrentHp <= 0) return score;
        for (CharacterStrategy strategy : REGISTRY.values()) strategy.evaluate(c, score);
        return score;
    }
    private static int rank(Map<String, Integer> ranks, String id) { return ranks.getOrDefault(id, 500); }
    private static boolean oneOf(String id, String... ids) {
        for (String candidate : ids) if (id.equals(candidate)) return true;
        return false;
    }
    private static final CharacterStrategy GENERIC = new CharacterStrategy() {
        public void evaluate(StrategyContext c, MechanismScore score) {}
        public int priority(StrategyContext c, StrategyContext.Card card, MonsterState target) { return 0; }
        public int defaultRank(String id) { return 500; }
    };

    private static final class Ironclad implements CharacterStrategy {
        public void evaluate(StrategyContext c, MechanismScore score) {
            score.add("strength-attacks", c.power("Strength") * Math.min(8, c.attacks()) * 10);
            int fuel = Math.min(5, c.exhaustFuel());
            score.add("exhaust-engine", fuel * (c.power("Feel No Pain") * 10
                    + c.power("Dark Embrace") * 25)
                    + (c.power("Corruption") != 0 ? fuel * 25 : 0));
            boolean retained = c.power("Barricade") != 0 || c.power("Blur") > 0;
            int block = c.features.playerBlock;
            if (!retained && ThreatEvaluator.hasRelic(c.state.playerState, "Calipers")) block = Math.max(0, block - 15);
            else if (!retained) block = 0;
            score.add("retained-block", Math.min(block, Math.max(0, c.features.totalEnemyHp)) * 10);
        }
        public int priority(StrategyContext c, StrategyContext.Card card, MonsterState target) {
            if (oneOf(card.id, Corruption.ID, DarkEmbrace.ID, FeelNoPain.ID)) {
                return c.exhaustFuel() > 0 || card.id.equals(Corruption.ID) && c.skills() > 0 ? 90 : -60;
            }
            if (card.id.equals(BodySlam.ID)) return c.features.playerBlock > 0 ? 10 : -60;
            if (card.id.equals(Entrench.ID)) return c.features.playerBlock > 0 ? 60 : -100;
            if (card.id.equals(SpotWeakness.ID)) return target != null && ThreatEvaluator.incomingHitCountOf(target) > 0 ? 70 : -100;
            if (card.id.equals(Dropkick.ID)) return target != null && CreaturePowerUtils.powerAmount(target, "Vulnerable") > 0 ? 100 : -10;
            if (card.exhaust && c.power("Corruption") != 0) return 30;
            if (card.block > 0 && c.handHas(BodySlam.ID)) return 40;
            return 0;
        }
        public int defaultRank(String id) { return rank(IronCladPlayOrder.CARD_RANKS, id); }
    }

    private static final class Silent implements CharacterStrategy {
        public void evaluate(StrategyContext c, MechanismScore score) {
            double poison = 0;
            for (MonsterState enemy : c.state.curMapNodeState.monsterData) {
                if (enemy.currentHealth <= 0) continue;
                int amount = Math.max(0, CreaturePowerUtils.powerAmount(enemy, "Poison"));
                // Only the next two ticks; HP loss already simulated is not included.
                double potential = amount + Math.max(0, amount - 1) * 0.5;
                poison += Math.min(enemy.currentHealth, potential);
            }
            score.add("poison-two-turns", (int) (poison * 10));
            int shivs = c.has(BladeDance.ID) || c.has(CloakAndDagger.ID) || c.has(Shiv.ID) ? 4 : 0;
            score.add("shiv-engine", shivs * (c.power("Accuracy") * 10 + c.power("After Image") * 12));
            int discardFuel = c.has(Acrobatics.ID) || c.has(Prepared.ID) || c.has(CalculatedGamble.ID) ? 1 : 0;
            score.add("discard-engine", discardFuel * ((c.has(Reflex.ID) ? 25 : 0) + (c.has(Tactician.ID) ? 35 : 0)));
            // Current-turn prevention is counted by threat/survival, only later protection is valued here.
            score.add("intangible-future", Math.max(0, c.power("IntangiblePlayer") - 1) * 30);
            score.add("wraith-dexterity-cost", Math.min(0, c.power("Wraith Form v2")) * 20);
        }
        public int priority(StrategyContext c, StrategyContext.Card card, MonsterState target) {
            if (oneOf(card.id, AfterImage.ID, Accuracy.ID, Footwork.ID)) return 80;
            if (card.id.equals(Catalyst.ID)) return target != null && CreaturePowerUtils.powerAmount(target, "Poison") > 0 ? 55 : -100;
            if (oneOf(card.id, DeadlyPoison.ID, BouncingFlask.ID, CripplingPoison.ID, PoisonedStab.ID)) return c.handHas(Catalyst.ID) ? 70 : 15;
            if (card.id.equals(SneakyStrike.ID)) return card.cost == 0 || c.state.getTotalDiscardedThisTurn() > 0 ? 95 : -10;
            if (card.id.equals(Eviscerate.ID)) return card.cost == 0 ? 95 : -10;
            if (card.id.equals(HeelHook.ID)) return target != null && CreaturePowerUtils.powerAmount(target, "Weakened") > 0 ? 100 : 0;
            if (oneOf(card.id, Acrobatics.ID, Prepared.ID, CalculatedGamble.ID)) return c.handHas(Reflex.ID) || c.handHas(Tactician.ID) || c.handHas(SneakyStrike.ID) ? 70 : 10;
            if (card.id.equals(WraithForm.ID)) return c.features.currentIncomingDamage > 0 && c.power("IntangiblePlayer") == 0 ? 85 : -70;
            if (card.id.equals(Concentrate.ID)) return c.hand.size() > (card.upgraded ? 3 : 4) && c.energy < 2 ? 40 : -80;
            return 0;
        }
        public int defaultRank(String id) { return rank(SilentPlayOrder.CARD_RANKS, id); }
    }

    private static final class Defect implements CharacterStrategy {
        public void evaluate(StrategyContext c, MechanismScore score) {
            double damage = 0, block = 0, energy = 0, dark = 0;
            boolean electrodynamics = c.power("Electrodynamics") != 0;
            if (c.state.playerState.orbs != null) {
                for (OrbState orb : c.state.playerState.orbs) {
                    if (orb instanceof LightningOrbState) damage += Math.max(0, orb.passiveAmount) * 1.5 * (electrodynamics ? c.features.aliveEnemyCount : 1);
                    else if (orb instanceof FrostOrbState) block += Math.max(0, orb.passiveAmount) * 1.5;
                    else if (orb instanceof PlasmaOrbState) energy += Math.max(0, orb.passiveAmount) * 1.5;
                    else if (orb instanceof DarkOrbState) {
                        // Stored damage only earns potential if an evoke is reachable; front slots matter.
                        boolean accessible = c.has(Dualcast.ID) || c.has(MultiCast.ID) || c.has(Recursion.ID);
                        if (accessible && c.state.playerState.orbs.indexOf(orb) == 0) dark += Math.max(0, orb.evokeAmount) * 0.5;
                    }
                }
            }
            score.add("orb-damage-two-turns", (int) (Math.min(c.features.totalEnemyHp, damage + dark) * 10));
            score.add("frost-two-turns", (int) (Math.min(block, c.features.currentIncomingDamage) * 15));
            int demand = 0;
            for (StrategyContext.Card card : c.hand) demand += Math.max(0, card.cost);
            score.add("plasma-demand", (int) (Math.min(energy, Math.max(0, demand - c.energy)) * 20));
            // Focus is already reflected in actual orb amounts, never added again.
            score.add("echo-future", Math.max(0, c.power("Echo Form")) * Math.min(4, c.availableCards.size()) * 15);
        }
        public int priority(StrategyContext c, StrategyContext.Card card, MonsterState target) {
            if (oneOf(card.id, Defragment.ID, Electrodynamics.ID, EchoForm.ID, Loop.ID)) return 80;
            if (card.id.equals(BiasedCognition.ID)) return c.state.playerState.orbs.isEmpty() ? -20 : 85;
            if (oneOf(card.id, Dualcast.ID, MultiCast.ID, Recursion.ID)) {
                if (c.state.playerState.orbs.isEmpty() || c.state.playerState.orbs.get(0) instanceof EmptyOrbSlotState) return -100;
                OrbState front = c.state.playerState.orbs.get(0);
                if (front instanceof FrostOrbState) return c.features.currentIncomingDamage > 0 ? 65 : -30;
                if (front instanceof DarkOrbState) return front.evokeAmount >= c.features.totalEnemyHp ? 100 : 20;
                return 35;
            }
            if (card.id.equals(Turbo.ID)) {
                int demand = 0;
                for (StrategyContext.Card other : c.hand) if (!other.id.equals(Turbo.ID)) demand += Math.max(0, other.cost);
                return demand > c.energy ? 70 : -90;
            }
            if (card.id.equals(Consume.ID)) return c.state.playerState.maxOrbs > 1 ? 65 : -80;
            if (oneOf(card.id, Glacier.ID, Coolheaded.ID, ColdSnap.ID)) return c.features.currentIncomingDamage > 0 ? 50 : 15;
            return 0;
        }
        public int defaultRank(String id) { return rank(DefectPlayOrder.CARD_RANKS, id); }
    }

    private static final class Watcher implements CharacterStrategy {
        public void evaluate(StrategyContext c, MechanismScore score) {
            // Wrath damage is represented by threat, never by a second flat penalty.
            score.add("calm-exit", "Calm".equals(c.stance) && canLeaveCalm(c) ? 40 : 0);
            int mantra = Math.max(0, c.power("Mantra"));
            score.add("mantra-progress", mantra * 8 + (mantra >= 7 ? 25 : 0));
            int retained = 0;
            for (StrategyContext.Card card : c.hand) if (card.retained && !card.unplayable()) retained += Math.min(20, Math.max(card.damage, card.block));
            score.add("retained-options", retained * 2);
            if (hasExit(c) && (c.has(Eruption.ID) || c.has(Tantrum.ID) || c.has(Crescendo.ID))) {
                score.add("stance-engine", c.power("Adaptation") * 25 + c.power("Controlled") * 15);
            }
        }
        private boolean hasExit(StrategyContext c) {
            for (StrategyContext.Card card : c.availableCards) if (isStanceExit(card.id)) return true;
            return false;
        }
        private boolean canLeaveCalm(StrategyContext c) {
            for (StrategyContext.Card card : c.availableCards) {
                if (oneOf(card.id, EmptyFist.ID, EmptyBody.ID, EmptyMind.ID, Eruption.ID,
                        Tantrum.ID, Crescendo.ID, Indignation.ID, Blasphemy.ID)) return true;
            }
            return false;
        }
        public int priority(StrategyContext c, StrategyContext.Card card, MonsterState target) {
            if (oneOf(card.id, TalkToTheHand.ID, Rushdown.ID, MentalFortress.ID)) return 90;
            if (oneOf(card.id, Eruption.ID, Tantrum.ID, Crescendo.ID, Indignation.ID)) {
                if ("Wrath".equals(c.stance)) return card.id.equals(Indignation.ID) ? 20 : -60;
                boolean lethal = target != null && card.damage >= target.currentHealth + target.currentBlock;
                return lethal || c.canExitWrath(c.energy - Math.max(0, card.cost)) || c.features.currentIncomingDamage == 0 ? 65 : -90;
            }
            if (isStanceExit(card.id)) {
                if ("Wrath".equals(c.stance)) {
                    // Exit after attacks while safe, before them when another hit is lethal.
                    return c.features.currentIncomingDamage >= c.features.playerCurrentHp ? 85 : -15;
                }
                return "Calm".equals(c.stance) && oneOf(card.id, EmptyFist.ID, EmptyBody.ID, EmptyMind.ID) ? 30 : -30;
            }
            if (card.id.equals(Blasphemy.ID)) return -100; // Search must demonstrate the lethal, not assume it.
            if (oneOf(card.id, Worship.ID, Pray.ID, Prostrate.ID)) return c.power("Mantra") + Math.max(0, card.magic) >= 10 ? 75 : 10;
            return 0;
        }
        public int defaultRank(String id) {
            if (oneOf(id, TalkToTheHand.ID, Rushdown.ID, MentalFortress.ID)) return 0;
            if (oneOf(id, Eruption.ID, Tantrum.ID, Crescendo.ID)) return 20;
            if (isStanceExit(id)) return 80;
            return 50;
        }
    }
}
