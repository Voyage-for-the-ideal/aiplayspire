package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;

public class CrushJointsAction extends AbstractGameAction {
    private AbstractMonster m;

    public CrushJointsAction(AbstractMonster monster, int vulnAmount) {
        this.m = monster;
        this.magicNumber = vulnAmount;
    }
    private int magicNumber;

    public void update() {
        if (AbstractDungeon.actionManager.cardsPlayedThisCombat.size() >= 2
                && ((AbstractCard) AbstractDungeon.actionManager.cardsPlayedThisCombat
                        .get(AbstractDungeon.actionManager.cardsPlayedThisCombat
                                .size() - 2)).type == AbstractCard.CardType.SKILL) {
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.m,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new VulnerablePower((AbstractCreature) this.m, this.magicNumber, false),
                    this.magicNumber));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * CrushJointsAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



