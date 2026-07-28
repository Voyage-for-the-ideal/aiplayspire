package com.megacrit.cardcrawl.actions.defect;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.AttackDamageRandomEnemyAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.RipAndTearEffect;

public class NewRipAndTearAction extends AttackDamageRandomEnemyAction {
    public NewRipAndTearAction(AbstractCard card) {
        super(card);
    }

    public void update() {
        if (!Settings.FAST_MODE) {
            addToTop((AbstractGameAction) new WaitAction(0.1F));
        }

        super.update();
        if (Settings.FAST_MODE) {
            addToTop((AbstractGameAction) new WaitAction(0.05F));
        } else {
            addToTop((AbstractGameAction) new WaitAction(0.2F));
        }

        if (this.target != null)
            addToTop((AbstractGameAction) new VFXAction((AbstractGameEffect) new RipAndTearEffect(this.target.hb.cX,
                    this.target.hb.cY, Color.RED, Color.GOLD)));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * NewRipAndTearAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



