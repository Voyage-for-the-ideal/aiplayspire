package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.RedMask;
import com.megacrit.cardcrawl.vfx.GainPennyEffect;

public class MaskedBandits extends AbstractEvent {
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Masked Bandits");
    public static final String ID = "Masked Bandits";
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String PAID_MSG_1 = DESCRIPTIONS[0];
    private static final String PAID_MSG_2 = DESCRIPTIONS[1];
    private static final String PAID_MSG_3 = DESCRIPTIONS[2];
    private static final String PAID_MSG_4 = DESCRIPTIONS[3];

    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, PAID_1, PAID_2, PAID_3, END;
    }

    public MaskedBandits() {
        this.body = DESCRIPTIONS[4];

        this.roomEventText.addDialogOption(OPTIONS[0]);
        this.roomEventText.addDialogOption(OPTIONS[1]);

        this.hasDialog = true;
        this.hasFocus = true;
        (AbstractDungeon.getCurrRoom()).monsters = MonsterHelper.getEncounter("Masked Bandits");
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
                        stealGold();
                        AbstractDungeon.player.loseGold(AbstractDungeon.player.gold);
                        this.roomEventText.updateBodyText(PAID_MSG_1);
                        this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                        this.roomEventText.clearRemainingOptions();
                        this.screen = CurScreen.PAID_1;
                        return;

                    case 1:
                        logMetric("Masked Bandits", "Fought Bandits");

                        if (Settings.isDailyRun) {
                            AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(30));
                        } else {
                            AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(25, 35));
                        }
                        if (AbstractDungeon.player.hasRelic("Red Mask")) {
                            AbstractDungeon.getCurrRoom().addRelicToRewards((AbstractRelic) new Circlet());
                        } else {
                            AbstractDungeon.getCurrRoom().addRelicToRewards((AbstractRelic) new RedMask());
                        }

                        enterCombat();
                        AbstractDungeon.lastCombatMetricKey = "Masked Bandits";
                        return;
                }
                break;
            case PAID_1:
                this.roomEventText.updateBodyText(PAID_MSG_2);
                this.screen = CurScreen.PAID_2;
                this.roomEventText.updateDialogOption(0, OPTIONS[2]);
                break;
            case PAID_2:
                this.roomEventText.updateBodyText(PAID_MSG_3);
                this.screen = CurScreen.PAID_3;
                this.roomEventText.updateDialogOption(0, OPTIONS[3]);
                break;
            case PAID_3:
                this.roomEventText.updateBodyText(PAID_MSG_4);
                this.roomEventText.updateDialogOption(0, OPTIONS[3]);
                this.screen = CurScreen.END;
                openMap();
                break;
            case END:
                openMap();
                break;
        }
    }

    private void stealGold() {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        if (((AbstractCreature) abstractPlayer).gold == 0) {
            return;
        }

        logMetricLoseGold("Masked Bandits", "Paid Fearfully", ((AbstractCreature) abstractPlayer).gold);
        CardCrawlGame.sound.play("GOLD_JINGLE");

        for (int i = 0; i < ((AbstractCreature) abstractPlayer).gold; i++) {
            AbstractMonster abstractMonster = (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster();
            AbstractDungeon.effectList.add(new GainPennyEffect((AbstractCreature) abstractMonster,
                    ((AbstractCreature) abstractPlayer).hb.cX, ((AbstractCreature) abstractPlayer).hb.cY,
                    ((AbstractCreature) abstractMonster).hb.cX, ((AbstractCreature) abstractMonster).hb.cY, false));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * MaskedBandits.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

