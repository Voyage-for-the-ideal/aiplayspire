package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;

public class SFXAction extends AbstractGameAction {
    private String key;
    private float pitchVar = 0.0F;
    private boolean adjust = false;

    public SFXAction(String key) {
        this(key, 0.0F, false);
    }

    public SFXAction(String key, float pitchVar) {
        this(key, pitchVar, false);
    }

    public SFXAction(String key, float pitchVar, boolean pitchAdjust) {
        this.key = key;
        this.pitchVar = pitchVar;
        this.adjust = pitchAdjust;
        this.actionType = ActionType.WAIT;
    }

    public void update() {
        if (!this.adjust) {
            CardCrawlGame.sound.play(this.key, this.pitchVar);
        } else {
            CardCrawlGame.sound.playA(this.key, this.pitchVar);
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * SFXAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



