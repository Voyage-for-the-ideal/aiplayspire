package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.AttackDamageRandomEnemyAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;
import com.megacrit.cardcrawl.vfx.combat.LightningEffect;

public class NewThunderStrikeAction extends AttackDamageRandomEnemyAction {
    public NewThunderStrikeAction(AbstractCard card) {
        super(card);
    }

    public void update() {
        if (!Settings.FAST_MODE) {
            addToTop((AbstractGameAction) new WaitAction(0.1F));
        }

        super.update();

        if (this.target != null) {
            addToTop((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new LightningEffect(this.target.drawX, this.target.drawY)));
            addToTop((AbstractGameAction) new VFXAction((AbstractGameEffect) new FlashAtkImgEffect(this.target.hb.cX,
                    this.target.hb.cY, this.attackEffect)));
            addToTop((AbstractGameAction) new SFXAction("ORB_LIGHTNING_EVOKE", 0.1F));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * NewThunderStrikeAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



