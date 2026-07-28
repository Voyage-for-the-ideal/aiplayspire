package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;

public class IndignationAction extends AbstractGameAction {
    public IndignationAction(int amount) {
        this.amount = amount;
    }

    public void update() {
        if (AbstractDungeon.player.stance.ID.equals("Wrath")) {
            for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) mo,
                        (AbstractCreature) AbstractDungeon.player,
                        (AbstractPower) new VulnerablePower((AbstractCreature) mo, this.amount, false), this.amount,
                        true, AttackEffect.NONE));

            }

        } else {

            addToBot(new ChangeStanceAction("Wrath"));
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * IndignationAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



