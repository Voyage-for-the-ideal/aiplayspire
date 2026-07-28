package com.megacrit.cardcrawl.events.city;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.EventStrings;

public class TheJoust
        extends AbstractImageEvent {
    public static final String ID = "The Joust";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("The Joust");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String HALT_MSG = DESCRIPTIONS[0];
    private static final String EXPL_MSG = DESCRIPTIONS[1];
    private static final String BET_AGAINST = DESCRIPTIONS[2];
    private static final String BET_FOR = DESCRIPTIONS[3];
    private static final String COMBAT_MSG = DESCRIPTIONS[4];
    private static final String NOODLES_WIN = DESCRIPTIONS[5];
    private static final String NOODLES_LOSE = DESCRIPTIONS[6];
    private static final String BET_WON_MSG = DESCRIPTIONS[7];
    private static final String BET_LOSE_MSG = DESCRIPTIONS[8];

    private boolean betFor;
    private boolean ownerWins;
    private CUR_SCREEN screen = CUR_SCREEN.HALT;

    private static final int WIN_OWNER = 250;
    private float joustTimer = 0.0F;
    private static final int WIN_MURDERER = 100;
    private static final int BET_AMT = 50;
    private int clangCount = 0;

    private enum CUR_SCREEN {
        HALT, EXPLANATION, PRE_JOUST, JOUST, COMPLETE;
    }

    public TheJoust() {
        super(NAME, HALT_MSG, "images/events/joust.jpg");
        this.imageEventText.setDialogOption(OPTIONS[0]);
    }

    public void update() {
        super.update();
        if (this.joustTimer != 0.0F) {
            this.joustTimer -= Gdx.graphics.getDeltaTime();
            if (this.joustTimer < 0.0F) {
                this.clangCount++;
                if (this.clangCount == 1) {
                    CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.MED, false);
                    CardCrawlGame.sound.play("BLUNT_HEAVY");
                    this.joustTimer = 1.0F;
                } else if (this.clangCount == 2) {
                    CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
                    CardCrawlGame.sound.play("BLUNT_FAST");
                    this.joustTimer = 0.25F;
                } else if (this.clangCount == 3) {
                    CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.LONG, false);
                    CardCrawlGame.sound.play("BLUNT_HEAVY");
                    this.joustTimer = 0.0F;
                }
            }
        }
    }

    protected void buttonEffect(int buttonPressed) {
        String tmp;
        switch (this.screen) {
            case HALT:
                this.imageEventText.updateBodyText(EXPL_MSG);
                this.imageEventText.updateDialogOption(0, OPTIONS[1] + '2' + OPTIONS[2] + 'd' + OPTIONS[3]);
                this.imageEventText.setDialogOption(OPTIONS[4] + '2' + OPTIONS[5] + 'ú' + OPTIONS[3]);
                this.screen = CUR_SCREEN.EXPLANATION;
                break;
            case EXPLANATION:
                if (buttonPressed == 0) {
                    this.betFor = false;
                    this.imageEventText.updateBodyText(BET_AGAINST);
                } else {
                    this.betFor = true;
                    this.imageEventText.updateBodyText(BET_FOR);
                }
                AbstractDungeon.player.loseGold(50);
                this.imageEventText.updateDialogOption(0, OPTIONS[6]);
                this.imageEventText.clearRemainingOptions();
                this.screen = CUR_SCREEN.PRE_JOUST;
                break;
            case PRE_JOUST:
                this.imageEventText.updateBodyText(COMBAT_MSG);
                this.imageEventText.updateDialogOption(0, OPTIONS[6]);
                this.ownerWins = AbstractDungeon.miscRng.randomBoolean(0.3F);
                this.screen = CUR_SCREEN.JOUST;
                this.joustTimer = 0.01F;
                break;
            case JOUST:
                this.imageEventText.updateDialogOption(0, OPTIONS[7]);

                if (this.ownerWins) {
                    tmp = NOODLES_WIN;
                    if (this.betFor) {
                        tmp = tmp + BET_WON_MSG;
                        AbstractDungeon.player.gainGold(250);
                        CardCrawlGame.sound.play("GOLD_GAIN");
                        logMetricGainAndLoseGold("The Joust", "Bet on Owner", 250, 50);
                    } else {
                        tmp = tmp + BET_LOSE_MSG;
                        logMetricLoseGold("The Joust", "Bet on Owner", 50);
                    }

                } else {

                    tmp = NOODLES_LOSE;
                    if (this.betFor) {
                        tmp = tmp + BET_LOSE_MSG;
                        logMetricLoseGold("The Joust", "Bet on Murderer", 50);
                    } else {
                        tmp = tmp + BET_WON_MSG;
                        AbstractDungeon.player.gainGold(100);
                        CardCrawlGame.sound.play("GOLD_GAIN");
                        logMetricGainAndLoseGold("The Joust", "Bet on Murderer", 100, 50);
                    }
                }
                this.imageEventText.updateBodyText(tmp);
                this.screen = CUR_SCREEN.COMPLETE;
                break;
            case COMPLETE:
                openMap();
                break;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * TheJoust.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

