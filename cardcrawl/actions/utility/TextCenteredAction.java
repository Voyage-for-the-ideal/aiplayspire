package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.TextCenteredEffect;

public class TextCenteredAction
        extends AbstractGameAction {
    private boolean used = false;
    private String msg;
    private static final float DURATION = 2.0F;

    public TextCenteredAction(AbstractCreature source, String text) {
        setValues(source, source);
        this.msg = text;
        this.duration = 2.0F;
        this.actionType = ActionType.TEXT;
    }

    public void update() {
        if (!this.used) {
            AbstractDungeon.effectList.add(new TextCenteredEffect(this.msg));
            this.used = true;
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * TextCenteredAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */



