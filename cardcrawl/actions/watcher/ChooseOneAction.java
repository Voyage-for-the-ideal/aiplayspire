package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.ArrayList;

public class ChooseOneAction
        extends AbstractGameAction {
    private ArrayList<AbstractCard> choices;

    public ChooseOneAction(ArrayList<AbstractCard> choices) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.choices = choices;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            AbstractDungeon.cardRewardScreen.chooseOneOpen(this.choices);
            tickDuration();

            return;
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * ChooseOneAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



