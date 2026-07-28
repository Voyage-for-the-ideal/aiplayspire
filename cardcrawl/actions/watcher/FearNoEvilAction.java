package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class FearNoEvilAction extends AbstractGameAction {
    private AbstractMonster m;
    private DamageInfo info;

    public FearNoEvilAction(AbstractMonster m, DamageInfo info) {
        this.m = m;
        this.info = info;
    }

    public void update() {
        if (this.m != null && (this.m.intent == AbstractMonster.Intent.ATTACK
                || this.m.intent == AbstractMonster.Intent.ATTACK_BUFF
                || this.m.intent == AbstractMonster.Intent.ATTACK_DEBUFF
                || this.m.intent == AbstractMonster.Intent.ATTACK_DEFEND)) {

            addToTop(new ChangeStanceAction("Calm"));
        }
        addToTop((AbstractGameAction) new DamageAction((AbstractCreature) this.m, this.info, AttackEffect.SLASH_HEAVY));
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * FearNoEvilAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



