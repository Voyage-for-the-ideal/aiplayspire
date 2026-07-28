package com.megacrit.cardcrawl.events.shrines;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class WomanInBlue
        extends AbstractImageEvent {
    public static final String ID = "The Woman in Blue";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("The Woman in Blue");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final int cost1 = 20;
    private static final int cost2 = 30;
    private static final int cost3 = 40;
    private static final float PUNCH_DMG_PERCENT = 0.05F;
    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, RESULT;
    }

    public WomanInBlue() {
        super(NAME, DIALOG_1, "images/events/ladyInBlue.jpg");
        this.noCardsInRewards = true;
        this.imageEventText.setDialogOption(OPTIONS[0] + '\024' + OPTIONS[3]);
        this.imageEventText.setDialogOption(OPTIONS[1] + '\036' + OPTIONS[3]);
        this.imageEventText.setDialogOption(OPTIONS[2] + '(' + OPTIONS[3]);
        if (AbstractDungeon.ascensionLevel >= 15) {
            this.imageEventText.setDialogOption(OPTIONS[5] +
                    MathUtils.ceil(AbstractDungeon.player.maxHealth * 0.05F) + OPTIONS[6]);
        } else {
            this.imageEventText.setDialogOption(OPTIONS[4]);
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {

                    case 0:
                        AbstractDungeon.player.loseGold(20);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                        logMetric("Bought 1 Potion");
                        (AbstractDungeon.getCurrRoom()).rewards.clear();
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
                        AbstractDungeon.combatRewardScreen.open();
                        break;

                    case 1:
                        AbstractDungeon.player.loseGold(30);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                        logMetric("Bought 2 Potions");
                        (AbstractDungeon.getCurrRoom()).rewards.clear();
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
                        AbstractDungeon.combatRewardScreen.open();
                        break;

                    case 2:
                        AbstractDungeon.player.loseGold(40);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                        logMetric("Bought 3 Potions");
                        (AbstractDungeon.getCurrRoom()).rewards.clear();
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).rewards.add(new RewardItem(PotionHelper.getRandomPotion()));
                        (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
                        AbstractDungeon.combatRewardScreen.open();
                        break;
                    case 3:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.MED,
                                false);

                        CardCrawlGame.sound.play("BLUNT_FAST");
                        if (AbstractDungeon.ascensionLevel >= 15) {
                            AbstractDungeon.player.damage(new DamageInfo(null,

                                    MathUtils.ceil(AbstractDungeon.player.maxHealth * 0.05F),
                                    DamageInfo.DamageType.HP_LOSS));
                        }

                        logMetric("Bought 0 Potions");
                        break;
                }
                this.imageEventText.clearAllDialogs();
                this.imageEventText.setDialogOption(OPTIONS[4]);
                this.screen = CurScreen.RESULT;
                return;
        }
        openMap();
    }

    public void logMetric(String actionTaken) {
        AbstractEvent.logMetric("The Woman in Blue", actionTaken);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\shrines\
 * WomanInBlue.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

