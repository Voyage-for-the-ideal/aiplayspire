package com.megacrit.cardcrawl.events.city;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.colorless.Apparition;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;
import java.util.List;

public class Ghosts
        extends AbstractImageEvent {
    public static final String ID = "Ghosts";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Ghosts");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;
    private static final String INTRO_BODY_M = DESCRIPTIONS[0];
    private static final String ACCEPT_BODY = DESCRIPTIONS[2];
    private static final String EXIT_BODY = DESCRIPTIONS[3];
    private static final float HP_DRAIN = 0.5F;
    private int screenNum = 0, hpLoss = 0;

    public Ghosts() {
        super(NAME, "test", "images/events/ghost.jpg");
        this.body = INTRO_BODY_M;

        this.hpLoss = MathUtils.ceil(AbstractDungeon.player.maxHealth * 0.5F);
        if (this.hpLoss >= AbstractDungeon.player.maxHealth) {
            this.hpLoss = AbstractDungeon.player.maxHealth - 1;
        }

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.imageEventText.setDialogOption(OPTIONS[3] + this.hpLoss + OPTIONS[1], (AbstractCard) new Apparition());
        } else {
            this.imageEventText.setDialogOption(OPTIONS[0] + this.hpLoss + OPTIONS[1], (AbstractCard) new Apparition());
        }

        this.imageEventText.setDialogOption(OPTIONS[2]);
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_GHOSTS");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {

                    case 0:
                        this.imageEventText.updateBodyText(ACCEPT_BODY);

                        AbstractDungeon.player.decreaseMaxHealth(this.hpLoss);

                        becomeGhost();

                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[5]);
                        this.imageEventText.clearRemainingOptions();
                        return;
                }

                logMetricIgnored("Ghosts");
                this.imageEventText.updateBodyText(EXIT_BODY);
                this.screenNum = 2;
                this.imageEventText.updateDialogOption(0, OPTIONS[5]);
                this.imageEventText.clearRemainingOptions();
                return;

            case 1:
                openMap();
                return;
        }

        openMap();
    }

    private void becomeGhost() {
        List<String> cards = new ArrayList<>();
        int amount = 5;
        if (AbstractDungeon.ascensionLevel >= 15) {
            amount -= 2;
        }
        for (int i = 0; i < amount; i++) {
            Apparition apparition = new Apparition();
            cards.add(((AbstractCard) apparition).cardID);
            AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) apparition, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F));
        }

        logMetricObtainCardsLoseMapHP("Ghosts", "Became a Ghost", cards, this.hpLoss);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\Ghosts
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

