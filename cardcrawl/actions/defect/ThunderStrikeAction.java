package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.vfx.combat.LightningEffect;

public class ThunderStrikeAction extends AbstractGameAction {
    private DamageInfo info;
    private static final float DURATION = 0.01F;
    private static final float POST_ATTACK_WAIT_DUR = 0.2F;
    private int numTimes;

    public ThunderStrikeAction(AbstractCreature target, DamageInfo info, int numTimes) {
        this.info = info;
        this.target = target;
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = AttackEffect.NONE;
        this.duration = 0.01F;
        this.numTimes = numTimes;
    }

    public void update() {
        if (this.target == null) {
            this.isDone = true;

            return;
        }
        if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
            AbstractDungeon.actionManager.clearPostCombatActions();
            this.isDone = true;

            return;
        }
        if (this.target.currentHealth > 0) {
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, this.attackEffect));
            AbstractDungeon.effectList.add(new LightningEffect(this.target.drawX, this.target.drawY));
            CardCrawlGame.sound.play("ORB_LIGHTNING_EVOKE", 0.1F);

            this.info.applyPowers(this.info.owner, this.target);
            this.target.damage(this.info);

            if (this.numTimes > 1 && !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
                this.numTimes--;
                addToTop(new ThunderStrikeAction(

                        (AbstractCreature) AbstractDungeon.getMonsters().getRandomMonster(null, true,
                                AbstractDungeon.cardRandomRng),
                        this.info, this.numTimes));
            }

            addToTop((AbstractGameAction) new WaitAction(0.2F));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * ThunderStrikeAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



