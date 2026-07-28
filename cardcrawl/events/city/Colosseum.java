package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class Colosseum
        extends AbstractImageEvent {
    public static final String ID = "Colosseum";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Colosseum");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, FIGHT, LEAVE, POST_COMBAT;
    }

    public Colosseum() {
        super(NAME, DESCRIPTIONS[0], "images/events/colosseum.jpg");

        this.imageEventText.setDialogOption(OPTIONS[0]);
    }

   protected void buttonEffect(int buttonPressed) {
     switch (this.screen) {
       case INTRO:
         switch (buttonPressed) {
           case 0:
             this.imageEventText.updateBodyText(DESCRIPTIONS[1] + DESCRIPTIONS[2]  + DESCRIPTIONS[3]);
             this.imageEventText.updateDialogOption(0, OPTIONS[1]);
             this.screen = CurScreen.FIGHT;
             break;
         }
         return;
       case FIGHT:
         switch (buttonPressed) {
           case 0:
             this.screen = CurScreen.POST_COMBAT;
             logMetric("Fight");
             (AbstractDungeon.getCurrRoom()).monsters = MonsterHelper.getEncounter("Colosseum Slavers");

             (AbstractDungeon.getCurrRoom()).rewards.clear();
             (AbstractDungeon.getCurrRoom()).rewardAllowed = false;
             enterCombatFromImage();
             AbstractDungeon.lastCombatMetricKey = "Colosseum Slavers";
             break;
         }


         this.imageEventText.clearRemainingOptions();
         return;
       case POST_COMBAT:
         (AbstractDungeon.getCurrRoom()).rewardAllowed = true;
         switch (buttonPressed) {
           case 1:
             this.screen = CurScreen.LEAVE;
             logMetric("Fought Nobs");
             (AbstractDungeon.getCurrRoom()).monsters = MonsterHelper.getEncounter("Colosseum Nobs");

             (AbstractDungeon.getCurrRoom()).rewards.clear();
             AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.RARE);
             AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.UNCOMMON);
             AbstractDungeon.getCurrRoom().addGoldToRewards(100);
             (AbstractDungeon.getCurrRoom()).eliteTrigger = true;
             enterCombatFromImage();
             AbstractDungeon.lastCombatMetricKey = "Colosseum Nobs";
             return;
         }
         logMetric("Fled From Nobs");
         openMap();
         return;


       case LEAVE:
         openMap();
         return;
     }
     openMap();
   }

    public void logMetric(String actionTaken) {
        AbstractEvent.logMetric("Colosseum", actionTaken);
    }

    public void reopen() {
        if (this.screen != CurScreen.LEAVE) {
            AbstractDungeon.resetPlayer();
            AbstractDungeon.player.drawX = Settings.WIDTH * 0.25F;
            AbstractDungeon.player.preBattlePrep();
            enterImageFromCombat();
            this.imageEventText.updateBodyText(DESCRIPTIONS[4]);
            this.imageEventText.updateDialogOption(0, OPTIONS[2]);
            this.imageEventText.setDialogOption(OPTIONS[3]);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * Colosseum.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

