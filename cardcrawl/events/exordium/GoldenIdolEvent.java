package com.megacrit.cardcrawl.events.exordium;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.curses.Injury;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.GoldenIdol;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class GoldenIdolEvent
        extends AbstractImageEvent {
    public static final String ID = "Golden Idol";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Golden Idol");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_START = DESCRIPTIONS[0];
    private static final String DIALOG_BOULDER = DESCRIPTIONS[1];
    private static final String DIALOG_CHOSE_RUN = DESCRIPTIONS[2];
    private static final String DIALOG_CHOSE_FIGHT = DESCRIPTIONS[3];
    private static final String DIALOG_CHOSE_FLAT = DESCRIPTIONS[4];
    private static final String DIALOG_IGNORE = DESCRIPTIONS[5];

    private int screenNum = 0;

    private static final float HP_LOSS_PERCENT = 0.25F;
    private static final float MAX_HP_LOSS_PERCENT = 0.08F;
    private static final float A_2_HP_LOSS_PERCENT = 0.35F;
    private static final float A_2_MAX_HP_LOSS_PERCENT = 0.1F;
    private int damage;
    private int maxHpLoss;
    private AbstractRelic relicMetric = null;

    public GoldenIdolEvent() {
        super(NAME, DIALOG_START, "images/events/goldenIdol.jpg");

        this.imageEventText.setDialogOption(OPTIONS[0], (AbstractRelic) new GoldenIdol());
        this.imageEventText.setDialogOption(OPTIONS[1]);

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.damage = (int) (AbstractDungeon.player.maxHealth * 0.35F);
            this.maxHpLoss = (int) (AbstractDungeon.player.maxHealth * 0.1F);
        } else {
            this.damage = (int) (AbstractDungeon.player.maxHealth * 0.25F);
            this.maxHpLoss = (int) (AbstractDungeon.player.maxHealth * 0.08F);
        }

        if (this.maxHpLoss < 1) {
            this.maxHpLoss = 1;
        }
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_GOLDEN");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        Injury injury;
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(DIALOG_BOULDER);

                        if (AbstractDungeon.player.hasRelic("Golden Idol")) {
                            this.relicMetric = RelicLibrary.getRelic("Circlet").makeCopy();
                        } else {
                            this.relicMetric = RelicLibrary.getRelic("Golden Idol").makeCopy();
                        }
                        AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2), (Settings.HEIGHT / 2),
                                this.relicMetric);

                        CardCrawlGame.screenShake.mildRumble(5.0F);
                        CardCrawlGame.sound.play("BLUNT_HEAVY");
                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[2], CardLibrary.getCopy("Injury"));
                        this.imageEventText.updateDialogOption(1, OPTIONS[3] + this.damage + OPTIONS[4]);
                        this.imageEventText.setDialogOption(OPTIONS[5] + this.maxHpLoss + OPTIONS[6]);
                        return;
                }

                this.imageEventText.updateBodyText(DIALOG_IGNORE);
                this.screenNum = 2;
                this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                this.imageEventText.clearRemainingOptions();
                AbstractEvent.logMetricIgnored("Golden Idol");
                return;

            case 1:
                switch (buttonPressed) {

                    case 0:
                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.MED,
                                false);
                        this.imageEventText.updateBodyText(DIALOG_CHOSE_RUN);
                        injury = new Injury();
                        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) injury,
                                Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                        this.screenNum = 2;
                        this.imageEventText.updateDialogOption(0, OPTIONS[1]);

                        AbstractEvent.logMetricObtainCardAndRelic("Golden Idol", "Take Wound", (AbstractCard) injury,
                                this.relicMetric);

                        this.imageEventText.clearRemainingOptions();
                        return;

                    case 1:
                        this.imageEventText.updateBodyText(DIALOG_CHOSE_FIGHT);
                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.MED,
                                false);
                        CardCrawlGame.sound.play("BLUNT_FAST");
                        AbstractDungeon.player.damage(new DamageInfo(null, this.damage));
                        this.screenNum = 2;
                        this.imageEventText.updateDialogOption(0, OPTIONS[1]);

                        AbstractEvent.logMetricObtainRelicAndDamage("Golden Idol", "Take Damage", this.relicMetric,
                                this.damage);

                        this.imageEventText.clearRemainingOptions();
                        return;

                    case 2:
                        this.imageEventText.updateBodyText(DIALOG_CHOSE_FLAT);
                        AbstractDungeon.player.decreaseMaxHealth(this.maxHpLoss);
                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.MED,
                                false);
                        CardCrawlGame.sound.play("BLUNT_FAST");
                        this.screenNum = 2;
                        this.imageEventText.updateDialogOption(0, OPTIONS[1]);

                        AbstractEvent.logMetricObtainRelicAndLoseMaxHP("Golden Idol", "Lose Max HP", this.relicMetric,
                                this.maxHpLoss);
                        this.imageEventText.clearRemainingOptions();
                        return;
                }
                openMap();
                return;

            case 2:
                openMap();
                return;
        }
        openMap();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\exordium\
 * GoldenIdolEvent.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

