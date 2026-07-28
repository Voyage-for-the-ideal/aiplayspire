package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class LetterOpener
        extends AbstractRelic {
    public static final String ID = "Letter Opener";
    private static final int DAMAGE = 5;

    public LetterOpener() {
        super("Letter Opener", "letterOpener.png", RelicTier.UNCOMMON, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\005' + this.DESCRIPTIONS[1];
    }

    public void atTurnStart() {
        this.counter = 0;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.SKILL) {
            this.counter++;

            if (this.counter % 3 == 0) {
                flash();
                this.counter = 0;
                addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                        DamageInfo.createDamageMatrix(5, true), DamageInfo.DamageType.THORNS,
                        AbstractGameAction.AttackEffect.SLASH_HEAVY));
            }
        }
    }

    public void onVictory() {
        this.counter = -1;
    }

    public AbstractRelic makeCopy() {
        return new LetterOpener();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * LetterOpener.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

