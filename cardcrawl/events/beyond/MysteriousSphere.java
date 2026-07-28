package com.megacrit.cardcrawl.events.beyond;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class MysteriousSphere extends AbstractEvent {
    public static final String ID = "Mysterious Sphere";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Mysterious Sphere");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String INTRO_MSG = DESCRIPTIONS[0];
    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, PRE_COMBAT, END;
    }

    public MysteriousSphere() {
        initializeImage("images/events/sphereClosed.png", 1120.0F * Settings.xScale,
                AbstractDungeon.floorY - 50.0F * Settings.scale);

        this.body = INTRO_MSG;
        this.roomEventText.addDialogOption(OPTIONS[0]);
        this.roomEventText.addDialogOption(OPTIONS[1]);

        this.hasDialog = true;
        this.hasFocus = true;
        (AbstractDungeon.getCurrRoom()).monsters = MonsterHelper.getEncounter("2 Orb Walkers");
    }

    public void update() {
        super.update();

        if (!RoomEventDialog.waitForInput) {
            buttonEffect(this.roomEventText.getSelectedOption());
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {
                    case 0:
                        this.screen = CurScreen.PRE_COMBAT;
                        this.roomEventText.updateBodyText(DESCRIPTIONS[1]);
                        this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                        this.roomEventText.clearRemainingOptions();
                        logMetric("Mysterious Sphere", "Fight");
                        return;
                    case 1:
                        this.screen = CurScreen.END;
                        this.roomEventText.updateBodyText(DESCRIPTIONS[2]);
                        this.roomEventText.updateDialogOption(0, OPTIONS[1]);
                        this.roomEventText.clearRemainingOptions();
                        logMetricIgnored("Mysterious Sphere");
                        return;
                }
                break;
            case PRE_COMBAT:
                if (Settings.isDailyRun) {
                    AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(50));
                } else {
                    AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(45, 55));
                }
                AbstractDungeon.getCurrRoom().addRelicToRewards(
                        AbstractDungeon.returnRandomScreenlessRelic(AbstractRelic.RelicTier.RARE));

                if (this.img != null) {
                    this.img.dispose();
                    this.img = null;
                }

                this.img = ImageMaster.loadImage("images/events/sphereOpen.png");

                enterCombat();
                AbstractDungeon.lastCombatMetricKey = "2 Orb Walkers";
                break;
            case END:
                openMap();
                break;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\beyond\
 * MysteriousSphere.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

