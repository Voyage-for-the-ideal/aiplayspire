package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class TriggerMarksAction extends AbstractGameAction {
    AbstractCard card;

    public TriggerMarksAction(AbstractCard callingCard) {
        this.card = callingCard;
    }

    public void update() {
        for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            for (AbstractPower p : mo.powers) {
                p.triggerMarks(this.card);
            }
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * TriggerMarksAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



