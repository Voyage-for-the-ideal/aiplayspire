package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class OrnamentalFan extends AbstractRelic {
    public static final String ID = "Ornamental Fan";

    public OrnamentalFan() {
        super("Ornamental Fan", "ornamentalFan.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }
    private static final int BLOCK = 4;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\004' + this.DESCRIPTIONS[1];
    }

    public void atTurnStart() {
        this.counter = 0;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            this.counter++;

            if (this.counter % 3 == 0) {
                flash();
                this.counter = 0;
                addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                        (AbstractCreature) AbstractDungeon.player, 4));
            }
        }
    }

    public void onVictory() {
        this.counter = -1;
    }

    public AbstractRelic makeCopy() {
        return new OrnamentalFan();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * OrnamentalFan.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

