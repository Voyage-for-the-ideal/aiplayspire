package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.PurgeCardEffect;

import java.util.ArrayList;
import java.util.List;

public class FountainOfCurseRemoval
        extends AbstractImageEvent {
    public static final String ID = "Fountain of Cleansing";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Fountain of Cleansing");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final String DIALOG_2 = DESCRIPTIONS[1];
    private static final String DIALOG_3 = DESCRIPTIONS[2];

    private int screenNum = 0;

    public FountainOfCurseRemoval() {
        super(NAME, DIALOG_1, "images/events/fountain.jpg");

        this.imageEventText.setDialogOption(OPTIONS[0]);
        this.imageEventText.setDialogOption(OPTIONS[1]);
    }

    public void onEnterRoom() {
        CardCrawlGame.music.playTempBGM("SHRINE");
        if (Settings.AMBIANCE_ON)
            CardCrawlGame.sound.play("EVENT_FOUNTAIN");
    }

    protected void buttonEffect(int buttonPressed) {
        List<String> curses;
        int i;
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(DIALOG_2);
                        curses = new ArrayList<>();
                        this.screenNum = 1;

                        for (i = AbstractDungeon.player.masterDeck.group.size() - 1; i >= 0; i--) {

                            if (((AbstractCard) AbstractDungeon.player.masterDeck.group
                                    .get(i)).type == AbstractCard.CardType.CURSE &&
                                    !((AbstractCard) AbstractDungeon.player.masterDeck.group.get(i)).inBottleFlame &&
                                    !((AbstractCard) AbstractDungeon.player.masterDeck.group.get(i)).inBottleLightning
                                    && ((AbstractCard) AbstractDungeon.player.masterDeck.group
                                            .get(i)).cardID != "AscendersBane"
                                    && ((AbstractCard) AbstractDungeon.player.masterDeck.group
                                            .get(i)).cardID != "CurseOfTheBell"
                                    && ((AbstractCard) AbstractDungeon.player.masterDeck.group
                                            .get(i)).cardID != "Necronomicurse") {

                                AbstractDungeon.effectList
                                        .add(new PurgeCardEffect(AbstractDungeon.player.masterDeck.group
                                                .get(i)));
                                curses.add(((AbstractCard) AbstractDungeon.player.masterDeck.group.get(i)).cardID);
                                AbstractDungeon.player.masterDeck.removeCard(AbstractDungeon.player.masterDeck.group
                                        .get(i));
                            }
                        }
                        logMetricRemoveCards("Fountain of Cleansing", "Removed Curses", curses);
                        this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                }
                logMetricIgnored("Fountain of Cleansing");
                this.imageEventText.updateBodyText(DIALOG_3);
                this.imageEventText.updateDialogOption(0, OPTIONS[1]);
                this.imageEventText.clearRemainingOptions();
                this.screenNum = 1;
                return;

            case 1:
                openMap();
                return;
        }

        openMap();
    }

    public void logMetric(String cardGiven) {
        AbstractEvent.logMetric("Fountain of Cleansing", cardGiven);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\shrines\
 * FountainOfCurseRemoval.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

