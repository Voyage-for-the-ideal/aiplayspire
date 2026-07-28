package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.curses.Pride;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.BlightStrings;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class GrotesqueTrophy extends AbstractBlight {
    public static final String ID = "GrotesqueTrophy";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("GrotesqueTrophy");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public GrotesqueTrophy() {
        super("GrotesqueTrophy", NAME, DESC[0] + '\003' + DESC[1], "trophy.png", false);
        this.counter = 1;
    }

    public void onEquip() {
        CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

        for (int i = 0; i < 3; i++) {
            Pride pride = new Pride();
            UnlockTracker.markCardAsSeen(((AbstractCard) pride).cardID);
            group.addToBottom(pride.makeCopy());
        }

        AbstractDungeon.gridSelectScreen.openConfirmationGrid(group, DESC[2]);
    }

    public void stack() {
        this.counter++;
        flash();

        CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

        for (int i = 0; i < 3; i++) {
            Pride pride = new Pride();
            UnlockTracker.markCardAsSeen(((AbstractCard) pride).cardID);
            group.addToBottom(pride.makeCopy());
        }

        AbstractDungeon.gridSelectScreen.openConfirmationGrid(group, DESC[2]);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\
 * GrotesqueTrophy.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



