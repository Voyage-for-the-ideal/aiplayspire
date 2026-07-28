package com.megacrit.cardcrawl.events.beyond;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.GoldenIdol;
import com.megacrit.cardcrawl.vfx.RainingGoldEffect;

public class MoaiHead
        extends AbstractImageEvent {
    public static final String ID = "The Moai Head";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("The Moai Head");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final float HP_LOSS_PERCENT = 0.125F;
    private static final float A_2_HP_LOSS_PERCENT = 0.18F;
    private int hpAmt = 0;

    private static final int goldAmount = 333;

    private static final String INTRO_BODY = DESCRIPTIONS[0];
    private int screenNum = 0;

    public MoaiHead() {
        super(NAME, INTRO_BODY, "images/events/moaiHead.jpg");

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.hpAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.18F);
        } else {
            this.hpAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.125F);
        }

        this.imageEventText.setDialogOption(OPTIONS[0] + this.hpAmt + OPTIONS[1]);
        if (AbstractDungeon.player.hasRelic("Golden Idol")) {
            this.imageEventText.setDialogOption(OPTIONS[2], !AbstractDungeon.player.hasRelic("Golden Idol"));
        } else {
            this.imageEventText.setDialogOption(OPTIONS[3], !AbstractDungeon.player.hasRelic("Golden Idol"));
        }
        this.imageEventText.setDialogOption(OPTIONS[4]);
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {

                    case 0:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);

                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.MED,
                                true);
                        CardCrawlGame.sound.play("BLUNT_HEAVY");
                        AbstractDungeon.player.maxHealth -= this.hpAmt;
                        if (AbstractDungeon.player.currentHealth > AbstractDungeon.player.maxHealth) {
                            AbstractDungeon.player.currentHealth = AbstractDungeon.player.maxHealth;
                        }
                        if (AbstractDungeon.player.maxHealth < 1) {
                            AbstractDungeon.player.maxHealth = 1;
                        }
                        AbstractDungeon.player.heal(AbstractDungeon.player.maxHealth);
                        logMetricHealAndLoseMaxHP("The Moai Head", "Heal", AbstractDungeon.player.maxHealth,
                                this.hpAmt);

                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[4]);
                        this.imageEventText.clearRemainingOptions();
                        return;

                    case 1:
                        logMetricGainGoldAndLoseRelic("The Moai Head", "Gave Idol", (AbstractRelic) new GoldenIdol(),
                                333);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                        this.screenNum = 1;
                        AbstractDungeon.player.loseRelic("Golden Idol");
                        AbstractDungeon.effectList.add(new RainingGoldEffect(333));
                        AbstractDungeon.player.gainGold(333);
                        this.imageEventText.updateDialogOption(0, OPTIONS[4]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                }

                logMetricIgnored("The Moai Head");
                this.imageEventText.updateBodyText(DESCRIPTIONS[3]);
                this.screenNum = 1;
                this.imageEventText.updateDialogOption(0, OPTIONS[4]);
                this.imageEventText.clearRemainingOptions();
                return;
        }

        openMap();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\beyond\
 * MoaiHead.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

