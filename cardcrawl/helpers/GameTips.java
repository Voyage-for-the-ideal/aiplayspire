package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.TutorialStrings;

import java.util.ArrayList;
import java.util.Collections;

public class GameTips {
    private static final TutorialStrings tutorialStrings = CardCrawlGame.languagePack.getTutorialString("Random Tips");
    public static final String[] LABEL = tutorialStrings.LABEL;

    private ArrayList<String> tips = new ArrayList<>();

    public GameTips() {
        initialize();
    }

    public void initialize() {
        Collections.addAll(this.tips, tutorialStrings.TEXT);

        if (!Settings.isConsoleBuild) {
            Collections.addAll(this.tips, (CardCrawlGame.languagePack.getTutorialString("PC Tips")).TEXT);
        }

        Collections.shuffle(this.tips);
    }

    public String getTip() {
        String retVal = this.tips.remove(MathUtils.random(this.tips.size() - 1));
        if (this.tips.isEmpty()) {
            initialize();
        }

        return retVal;
    }

    public String getPotionTip() {
        return LABEL[0];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\GameTips.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

