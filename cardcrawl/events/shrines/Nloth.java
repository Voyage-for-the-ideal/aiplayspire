package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.NlothsGift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Nloth
        extends AbstractImageEvent {
    public static final String ID = "N'loth";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("N'loth");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final String DIALOG_2 = DESCRIPTIONS[1];
    private static final String DIALOG_3 = DESCRIPTIONS[2];

    private int screenNum = 0;
    private AbstractRelic choice1;

    public Nloth() {
        super(NAME, DIALOG_1, "images/events/nloth.jpg");

        ArrayList<AbstractRelic> relics = new ArrayList<>();
        relics.addAll(AbstractDungeon.player.relics);
        Collections.shuffle(relics, new Random(AbstractDungeon.miscRng.randomLong()));

        this.choice1 = relics.get(0);
        this.choice2 = relics.get(1);
        this.gift = (AbstractRelic) new NlothsGift();

        this.imageEventText.setDialogOption(OPTIONS[0] + this.choice1.name + OPTIONS[1],
                (AbstractRelic) new NlothsGift());
        this.imageEventText.setDialogOption(OPTIONS[0] + this.choice2.name + OPTIONS[1],
                (AbstractRelic) new NlothsGift());
        this.imageEventText.setDialogOption(OPTIONS[2]);
    }
    private AbstractRelic choice2;
    private AbstractRelic gift;

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_SERPENT");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(DIALOG_2);
                        if (AbstractDungeon.player.hasRelic("Nloth's Gift")) {
                            this.gift = (AbstractRelic) new Circlet();
                            AbstractEvent.logMetricRelicSwap("N'loth", "Traded Relic", this.gift, this.choice1);
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2),
                                    (Settings.HEIGHT / 2), this.gift);

                        } else {

                            AbstractEvent.logMetricRelicSwap("N'loth", "Traded Relic", this.gift, this.choice1);
                            AbstractDungeon.player.loseRelic(this.choice1.relicId);
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2),
                                    (Settings.HEIGHT / 2), this.gift);
                        }

                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                    case 1:
                        this.imageEventText.updateBodyText(DIALOG_2);
                        if (AbstractDungeon.player.hasRelic("Nloth's Gift")) {
                            this.gift = (AbstractRelic) new Circlet();
                            AbstractEvent.logMetricRelicSwap("N'loth", "Traded Relic", this.gift, this.choice2);
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2),
                                    (Settings.HEIGHT / 2), this.gift);

                        } else {

                            AbstractEvent.logMetricRelicSwap("N'loth", "Traded Relic", this.gift, this.choice2);
                            AbstractDungeon.player.loseRelic(this.choice2.relicId);
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain((Settings.WIDTH / 2),
                                    (Settings.HEIGHT / 2), this.gift);
                        }

                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                    case 2:
                        AbstractEvent.logMetricIgnored("N'loth");
                        this.imageEventText.updateBodyText(DIALOG_3);
                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                }
                this.imageEventText.updateBodyText(DIALOG_3);
                this.screenNum = 1;
                this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                this.imageEventText.clearRemainingOptions();
                return;

            case 1:
                openMap();
                return;
        }

        openMap();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\shrines\
 * Nloth.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

