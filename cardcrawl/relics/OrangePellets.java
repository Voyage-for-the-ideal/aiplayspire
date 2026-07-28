package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.unique.RemoveDebuffsAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class OrangePellets extends AbstractRelic {
    public static final String ID = "OrangePellets";
    private static boolean SKILL = false, POWER = false, ATTACK = false;

    public OrangePellets() {
        super("OrangePellets", "pellets.png", RelicTier.SHOP, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atTurnStart() {
        SKILL = false;
        POWER = false;
        ATTACK = false;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            ATTACK = true;
        } else if (card.type == AbstractCard.CardType.SKILL) {
            SKILL = true;
        } else if (card.type == AbstractCard.CardType.POWER) {
            POWER = true;
        }

        if (ATTACK && SKILL && POWER) {
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));

            addToBot((AbstractGameAction) new RemoveDebuffsAction((AbstractCreature) AbstractDungeon.player));
            SKILL = false;
            POWER = false;
            ATTACK = false;
        }
    }

    public AbstractRelic makeCopy() {
        return new OrangePellets();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * OrangePellets.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

