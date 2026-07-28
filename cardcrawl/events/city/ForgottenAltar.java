package com.megacrit.cardcrawl.events.city;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.curses.Decay;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.BloodyIdol;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.GoldenIdol;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class ForgottenAltar
        extends AbstractImageEvent {
    public static final String ID = "Forgotten Altar";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Forgotten Altar");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final String DIALOG_2 = DESCRIPTIONS[1];
    private static final String DIALOG_3 = DESCRIPTIONS[2];
    private static final String DIALOG_4 = DESCRIPTIONS[3];
    private static final float HP_LOSS_PERCENT = 0.25F;
    private static final float A_2_HP_LOSS_PERCENT = 0.35F;
    private int hpLoss;
    private static final int MAX_HP_GAIN = 5;

    public ForgottenAltar() {
        super(NAME, DIALOG_1, "images/events/forgottenAltar.jpg");

        if (AbstractDungeon.player.hasRelic("Golden Idol")) {
            this.imageEventText.setDialogOption(OPTIONS[0],

                    !AbstractDungeon.player.hasRelic("Golden Idol"), (AbstractRelic) new BloodyIdol());
        } else {

            this.imageEventText.setDialogOption(OPTIONS[1],

                    !AbstractDungeon.player.hasRelic("Golden Idol"), (AbstractRelic) new BloodyIdol());
        }

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.hpLoss = MathUtils.round(AbstractDungeon.player.maxHealth * 0.35F);
        } else {
            this.hpLoss = MathUtils.round(AbstractDungeon.player.maxHealth * 0.25F);
        }

        this.imageEventText.setDialogOption(OPTIONS[2] + '\005' + OPTIONS[3] + this.hpLoss + OPTIONS[4]);
        this.imageEventText.setDialogOption(OPTIONS[6], CardLibrary.getCopy("Decay"));
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_FORGOTTEN");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        Decay decay;
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {
                    case 0:
                        gainChalice();
                        showProceedScreen(DIALOG_2);
                        CardCrawlGame.sound.play("HEAL_1");
                        break;
                    case 1:
                        AbstractDungeon.player.increaseMaxHp(5, false);
                        AbstractDungeon.player.damage(new DamageInfo(null, this.hpLoss));
                        CardCrawlGame.sound.play("HEAL_3");
                        showProceedScreen(DIALOG_3);
                        logMetricDamageAndMaxHPGain("Forgotten Altar", "Shed Blood", this.hpLoss, 5);
                        break;
                    case 2:
                        CardCrawlGame.sound.play("BLUNT_HEAVY");
                        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.MED,
                                true);
                        decay = new Decay();
                        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) decay,
                                (Settings.WIDTH / 2), (Settings.HEIGHT / 2)));

                        showProceedScreen(DIALOG_4);
                        logMetricObtainCard("Forgotten Altar", "Smashed Altar", (AbstractCard) decay);
                        break;
                }

                return;
        }

        openMap();
    }

    public void gainChalice() {
        int relicAtIndex = 0;
        for (int i = 0; i < AbstractDungeon.player.relics.size(); i++) {
            if (((AbstractRelic) AbstractDungeon.player.relics.get(i)).relicId.equals("Golden Idol")) {
                relicAtIndex = i;
                break;
            }
        }
        if (AbstractDungeon.player.hasRelic("Bloody Idol")) {
            AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2), (Settings.HEIGHT / 2),

                    RelicLibrary.getRelic("Circlet").makeCopy());
            logMetricRelicSwap("Forgotten Altar", "Gave Idol", (AbstractRelic) new Circlet(),
                    (AbstractRelic) new GoldenIdol());
        } else {
            ((AbstractRelic) AbstractDungeon.player.relics.get(relicAtIndex)).onUnequip();
            AbstractRelic bloodyIdol = RelicLibrary.getRelic("Bloody Idol").makeCopy();
            bloodyIdol.instantObtain(AbstractDungeon.player, relicAtIndex, false);
            logMetricRelicSwap("Forgotten Altar", "Gave Idol", (AbstractRelic) new BloodyIdol(),
                    (AbstractRelic) new GoldenIdol());
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * ForgottenAltar.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

