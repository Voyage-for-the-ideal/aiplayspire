package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.colorless.RitualDagger;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.RainingGoldEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class Nest
        extends AbstractImageEvent {
    public static final String ID = "Nest";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Nest");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;
    private static final String INTRO_BODY_M = DESCRIPTIONS[0];
    private static final String INTRO_BODY_M_2 = DESCRIPTIONS[1];
    private static final String ACCEPT_BODY = DESCRIPTIONS[2];
    private static final String EXIT_BODY = DESCRIPTIONS[3];

    private static final int HP_LOSS = 6;
    private int goldGain;
    private int screenNum = 0;

    public Nest() {
        super(NAME, INTRO_BODY_M, "images/events/theNest.jpg");
        this.imageEventText.setDialogOption(OPTIONS[5]);

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.goldGain = 50;
        } else {
            this.goldGain = 99;
        }
    }

    protected void buttonEffect(int buttonPressed) {
        RitualDagger ritualDagger;
        switch (this.screenNum) {
            case 0:
                this.imageEventText.updateBodyText(INTRO_BODY_M_2);
                this.imageEventText.setDialogOption(OPTIONS[0] + '\006' + OPTIONS[1],
                        (AbstractCard) new RitualDagger());
                UnlockTracker.markCardAsSeen("RitualDagger");
                this.imageEventText.updateDialogOption(0, OPTIONS[2] + this.goldGain + OPTIONS[3]);
                this.screenNum = 1;
                return;

            case 1:
                switch (buttonPressed) {

                    case 0:
                        logMetricGainGold("Nest", "Stole From Cult", this.goldGain);
                        this.imageEventText.updateBodyText(EXIT_BODY);
                        this.screenNum = 2;
                        AbstractDungeon.effectList.add(new RainingGoldEffect(this.goldGain));
                        AbstractDungeon.player.gainGold(this.goldGain);
                        this.imageEventText.updateDialogOption(0, OPTIONS[4]);
                        this.imageEventText.clearRemainingOptions();
                        break;
                    case 1:
                        ritualDagger = new RitualDagger();
                        logMetricObtainCardAndDamage("Nest", "Joined the Cult", (AbstractCard) ritualDagger, 6);
                        this.imageEventText.updateBodyText(ACCEPT_BODY);
                        AbstractDungeon.player.damage(new DamageInfo(null, 6));
                        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) ritualDagger,
                                Settings.WIDTH * 0.3F, Settings.HEIGHT / 2.0F));

                        this.screenNum = 2;
                        this.imageEventText.updateDialogOption(0, OPTIONS[4]);
                        this.imageEventText.clearRemainingOptions();
                        break;
                }

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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\Nest.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

