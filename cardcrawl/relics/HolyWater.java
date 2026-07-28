package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class HolyWater extends AbstractRelic {
    public HolyWater() {
        super("HolyWater", "holy_water.png", RelicTier.BOSS, LandingSound.MAGICAL);
    }
    public static final String ID = "HolyWater";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atBattleStartPreDraw() {
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new MakeTempCardInHandAction((AbstractCard) new Miracle(), 3, false));
    }

    public boolean canSpawn() {
        return AbstractDungeon.player.hasRelic("PureWater");
    }

    public AbstractRelic makeCopy() {
        return new HolyWater();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\HolyWater.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

