package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class BlueCandle extends AbstractRelic {
    public static final String ID = "Blue Candle";
    public static final int HP_LOSS = 1;

    public BlueCandle() {
        super("Blue Candle", "blueCandle.png", RelicTier.UNCOMMON, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public AbstractRelic makeCopy() {
        return new BlueCandle();
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.CURSE) {
            AbstractDungeon.player.getRelic("Blue Candle").flash();
            addToBot((AbstractGameAction) new LoseHPAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, 1, AbstractGameAction.AttackEffect.FIRE));

            card.exhaust = true;
            action.exhaustCard = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\BlueCandle.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

