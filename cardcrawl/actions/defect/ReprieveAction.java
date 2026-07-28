package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FocusPower;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;

public class ReprieveAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ReprieveAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private int focusIncrease;
    private AbstractMonster targetMonster;

    public ReprieveAction(int increaseAmt, AbstractMonster m) {
        this.duration = 0.0F;
        this.actionType = ActionType.WAIT;
        this.focusIncrease = increaseAmt;
        this.targetMonster = m;
    }

    public void update() {
        if (this.targetMonster != null && this.targetMonster.intent != AbstractMonster.Intent.ATTACK
                && this.targetMonster.intent != AbstractMonster.Intent.ATTACK_BUFF
                && this.targetMonster.intent != AbstractMonster.Intent.ATTACK_DEBUFF
                && this.targetMonster.intent != AbstractMonster.Intent.ATTACK_DEFEND) {

            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new FocusPower((AbstractCreature) AbstractDungeon.player, this.focusIncrease),
                    this.focusIncrease));

        } else {

            AbstractDungeon.effectList.add(new ThoughtBubble(AbstractDungeon.player.dialogX,
                    AbstractDungeon.player.dialogY, 3.0F, TEXT[0], true));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * ReprieveAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



