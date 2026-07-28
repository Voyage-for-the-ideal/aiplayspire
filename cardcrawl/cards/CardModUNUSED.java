package com.megacrit.cardcrawl.cards;

public class CardModUNUSED {
    public String key;
    private EffectType type;
    public DurationType dur;
    private int amount;
    private boolean applied = false;

    public CardModUNUSED(EffectType type, DurationType dur, int amount, String key) {
        this.type = type;
        this.dur = dur;
        this.amount = amount;
        this.key = key;
    }

    public enum EffectType {
        DAMAGE;
    }

    public enum DurationType {
        ONE_TURN, COMBAT, ATTACKS_PLAYED, CARDS_PLAYED;
    }

    public void apply(AbstractCard card) {
        if (!this.applied) {
            this.applied = true;
            switch (this.type) {
                case DAMAGE:
                    card.damage += this.amount;
                    break;
            }
        }
    }

    public int applyDamageMod(int baseDamage) {
        return baseDamage + this.amount;
    }

    public void unapply(AbstractCard card) {
        switch (this.type) {
            case DAMAGE:
                card.damage -= this.amount;
                break;
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\
 * CardModUNUSED.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



