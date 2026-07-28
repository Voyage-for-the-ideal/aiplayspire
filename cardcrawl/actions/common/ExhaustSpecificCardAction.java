package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ExhaustSpecificCardAction
        extends AbstractGameAction {
    private AbstractCard targetCard;

    public ExhaustSpecificCardAction(AbstractCard targetCard, CardGroup group, boolean isFast) {
        this.targetCard = targetCard;
        setValues((AbstractCreature) AbstractDungeon.player, (AbstractCreature) AbstractDungeon.player, this.amount);
        this.actionType = ActionType.EXHAUST;
        this.group = group;

        this.startingDuration = Settings.ACTION_DUR_FAST;
        this.duration = this.startingDuration;
    }
    private CardGroup group;
    private float startingDuration;
    public ExhaustSpecificCardAction(AbstractCard targetCard, CardGroup group) {
        this(targetCard, group, false);
    }

    public void update() {
        if (this.duration == this.startingDuration &&
                this.group.contains(this.targetCard)) {
            this.group.moveToExhaustPile(this.targetCard);
            CardCrawlGame.dungeon.checkForPactAchievement();
            this.targetCard.exhaustOnUseOnce = false;
            this.targetCard.freeToPlayOnce = false;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ExhaustSpecificCardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



