package com.megacrit.cardcrawl.actions.utility;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.TextAboveCreatureEffect;

public class TextAboveCreatureAction
        extends AbstractGameAction {
    private boolean used = false;
    private String msg;

    public enum TextType {
        STUNNED, INTERRUPTED;
    }

    public TextAboveCreatureAction(AbstractCreature source, TextType type) {
        if (type == TextType.STUNNED) {
            setValues(source, source);
            this.msg = AbstractCreature.TEXT[3];
            this.actionType = ActionType.TEXT;
            this.duration = Settings.ACTION_DUR_FASTER;
        } else if (type == TextType.INTERRUPTED) {
            setValues(source, source);
            this.msg = AbstractCreature.TEXT[4];
            this.actionType = ActionType.TEXT;
            this.duration = Settings.ACTION_DUR_FASTER;
        } else {
            this.isDone = true;
        }
    }

    public TextAboveCreatureAction(AbstractCreature source, String text) {
        setValues(source, source);
        this.msg = text;
        this.actionType = ActionType.TEXT;
        this.duration = Settings.ACTION_DUR_FASTER;
    }

    public void update() {
        if (!this.used) {
            AbstractDungeon.effectList.add(new TextAboveCreatureEffect(this.source.hb.cX - this.source.animX,
                    this.source.hb.cY + this.target.hb.height / 2.0F, this.msg, Color.WHITE

                            .cpy()));
            this.used = true;
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * TextAboveCreatureAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */



