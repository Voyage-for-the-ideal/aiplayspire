package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DamagePerAttackPlayedAction
        extends AbstractGameAction {
    private DamageInfo info;

    public DamagePerAttackPlayedAction(AbstractCreature target, DamageInfo info, AttackEffect effect) {
        this.info = info;
        setValues(target, info);
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = effect;
    }

    public DamagePerAttackPlayedAction(AbstractCreature target, DamageInfo info) {
        this(target, info, AttackEffect.NONE);
    }

    public void update() {
        this.isDone = true;
        if (this.target != null && this.target.currentHealth > 0) {
            int count = 0;
            for (AbstractCard c : AbstractDungeon.actionManager.cardsPlayedThisTurn) {
                if (c.type == AbstractCard.CardType.ATTACK) {
                    count++;
                }
            }

            count--;
            for (int i = 0; i < count; i++)
                addToTop((AbstractGameAction) new DamageAction(this.target, this.info, this.attackEffect));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DamagePerAttackPlayedAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



