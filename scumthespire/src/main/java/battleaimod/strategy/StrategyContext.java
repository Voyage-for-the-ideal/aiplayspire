package battleaimod.strategy;

import battleaimod.evaluation.CombatFeatures;
import battleaimod.evaluation.CreaturePowerUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import savestate.CardState;
import savestate.SaveState;
import savestate.StateFactories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable view per decision; scoring never loads cards or touches the live engine. */
public final class StrategyContext {
    public final SaveState state;
    public final CombatFeatures features;
    public final List<Card> hand;
    public final List<Card> availableCards;
    public final int energy;
    public final String stance;

    public StrategyContext(SaveState state, CombatFeatures features) {
        this.state = state;
        this.features = features;
        this.energy = state.playerState.energyPanelTotalEnergy;
        this.stance = state.playerState.getStanceId();
        List<Card> cards = new ArrayList<>();
        append(cards, state.playerState.hand);
        this.hand = Collections.unmodifiableList(new ArrayList<>(cards));
        append(cards, state.playerState.drawPile);
        append(cards, state.playerState.discardPile);
        this.availableCards = Collections.unmodifiableList(cards);
    }

    private static void append(List<Card> cards, CardState[] pile) {
        if (pile == null) return;
        for (CardState card : pile) cards.add(Card.from(card));
    }

    public int power(String id) { return CreaturePowerUtils.powerAmount(state.playerState, id); }
    public boolean has(String id) {
        for (Card card : availableCards) if (id.equals(card.id)) return true;
        return false;
    }
    public boolean handHas(String id) {
        for (Card card : hand) if (id.equals(card.id)) return true;
        return false;
    }
    public int attacks() {
        int count = 0;
        for (Card card : availableCards) if (card.type == AbstractCard.CardType.ATTACK
                || (card.type == null && card.damage > 0)) count++;
        return count;
    }
    public int skills() {
        int count = 0;
        for (Card card : availableCards) if (card.type == AbstractCard.CardType.SKILL
                || (card.type == null && card.block > 0)) count++;
        return count;
    }
    public int exhaustFuel() {
        if (power("Corruption") != 0) return skills();
        int count = 0;
        for (Card card : availableCards) if (card.exhaust) count++;
        if (has("True Grit") || has("Second Wind") || has("Fiend Fire") || has("Burning Pact")) {
            count = Math.max(count, Math.max(0, availableCards.size() - 1));
        }
        return count;
    }
    public boolean canExitWrath(int remainingEnergy) {
        for (Card card : hand) {
            if (card.cost <= remainingEnergy && card.cost >= 0 && CharacterStrategies.isStanceExit(card.id)) {
                if (!"FearNoEvil".equals(card.id) || features.currentIncomingHitCount > 0) return true;
            }
        }
        return false;
    }

    public static final class Card {
        public final String id;
        public final int cost, damage, block, magic;
        public final boolean exhaust, retained, ethereal, upgraded;
        public final AbstractCard.CardType type;
        public final AbstractCard.CardColor color;

        public Card(String id, int cost, int damage, int block, int magic, boolean exhaust,
                    boolean retained, boolean ethereal, boolean upgraded,
                    AbstractCard.CardType type, AbstractCard.CardColor color) {
            this.id = id; this.cost = cost; this.damage = damage; this.block = block; this.magic = magic;
            this.exhaust = exhaust; this.retained = retained; this.ethereal = ethereal;
            this.upgraded = upgraded; this.type = type; this.color = color;
        }
        public static Card from(CardState card) {
            AbstractCard template = CardLibrary.cards.get(card.getName());
            return new Card(StateFactories.cardIds[card.cardIdIndex], card.isFreeToPlayOnce() ? 0 : card.getCostForTurn(),
                    card.getDamage(), card.getBlock(), card.getMagicNumber(), card.isExhaust(),
                    card.isRetained(), card.isEthereal(), card.upgraded,
                    template == null ? null : template.type, template == null ? null : template.color);
        }
        public static Card from(AbstractCard card) {
            return new Card(card.cardID, card.freeToPlayOnce ? 0 : card.costForTurn,
                    card.damage, card.block, card.magicNumber, card.exhaust, card.retain || card.selfRetain,
                    card.isEthereal, card.upgraded, card.type, card.color);
        }
        public boolean unplayable() {
            return cost == -2 || type == AbstractCard.CardType.CURSE || type == AbstractCard.CardType.STATUS;
        }
    }
}
