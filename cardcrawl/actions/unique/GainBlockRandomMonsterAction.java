package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

import java.util.ArrayList;

public class GainBlockRandomMonsterAction
        extends AbstractGameAction {
    public GainBlockRandomMonsterAction(AbstractCreature source, int amount) {
        this.duration = 0.5F;
        this.source = source;
        this.amount = amount;
        this.actionType = ActionType.BLOCK;
    }

    public void update() {
        if (this.duration == 0.5F) {
            ArrayList<AbstractMonster> validMonsters = new ArrayList<>();
            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                if (m != this.source && m.intent != AbstractMonster.Intent.ESCAPE && !m.isDying) {
                    validMonsters.add(m);
                }
            }

            if (!validMonsters.isEmpty()) {
                this.target = (AbstractCreature) validMonsters
                        .get(AbstractDungeon.aiRng.random(validMonsters.size() - 1));
            } else {
                this.target = this.source;
            }

            if (this.target != null) {
                AbstractDungeon.effectList
                        .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AttackEffect.SHIELD));
                this.target.addBlock(this.amount);
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * GainBlockRandomMonsterAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



