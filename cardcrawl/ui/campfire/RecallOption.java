package com.megacrit.cardcrawl.ui.campfire;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.vfx.campfire.CampfireRecallEffect;

public class RecallOption
        extends AbstractCampfireOption {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("Recall Option");
    public static final String[] TEXT = uiStrings.TEXT;

    public void useOption() {
        AbstractDungeon.effectList.add(new CampfireRecallEffect());
    }

    public RecallOption() {
        this.label = TEXT[0];
        this.description = TEXT[1];
        this.img= ImageMaster.CAMPFIRE_RECALL_BUTTON;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\campfire\
 * RecallOption.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

