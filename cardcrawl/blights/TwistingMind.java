package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.status.*;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class TwistingMind extends AbstractBlight {
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("TwistingMind");
    public static final String ID = "TwistingMind";
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public TwistingMind() {
        super("TwistingMind", NAME, DESC[0] + '\001' + DESC[1], "twist.png", false);
        this.counter = 1;
    }

    public void stack() {
        this.counter++;
        updateDescription();
        flash();
    }

    public void updateDescription() {
        this.description = DESC[0] + this.counter + DESC[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onPlayerEndTurn() {
        flash();
        CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

        group.addToBottom((AbstractCard) new Slimed());
        group.addToBottom((AbstractCard) new VoidCard());
        group.addToBottom((AbstractCard) new Burn());
        group.addToBottom((AbstractCard) new Dazed());
        group.addToBottom((AbstractCard) new Wound());
        group.shuffle();

        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new MakeTempCardInDrawPileAction(group
                .getBottomCard(), this.counter, false, true));

        group.clear();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\
 * TwistingMind.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



