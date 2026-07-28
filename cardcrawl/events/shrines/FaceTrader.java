package com.megacrit.cardcrawl.events.shrines;

import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.vfx.RainingGoldEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class FaceTrader
        extends AbstractImageEvent {
    public static final String ID = "FaceTrader";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("FaceTrader");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;
    private static int goldReward;
    private static int damage;
    private CurScreen screen = CurScreen.INTRO;

    private enum CurScreen {
        INTRO, MAIN, RESULT;
    }

    public FaceTrader() {
        super(NAME, DESCRIPTIONS[0], "images/events/facelessTrader.jpg");

        if (AbstractDungeon.ascensionLevel >= 15) {
            goldReward = 50;
        } else {
            goldReward = 75;
        }

        damage = AbstractDungeon.player.maxHealth / 10;
        if (damage == 0) {
            damage = 1;
        }

        this.imageEventText.setDialogOption(OPTIONS[4]);
    }

    protected void buttonEffect(int buttonPressed) {
        AbstractRelic r;
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                        this.imageEventText.updateDialogOption(0,
                                OPTIONS[0] + damage + OPTIONS[5] + goldReward + OPTIONS[1]);

                        this.imageEventText.setDialogOption(OPTIONS[2]);
                        this.imageEventText.setDialogOption(OPTIONS[3]);
                        this.screen = CurScreen.MAIN;
                        break;
                }
                return;
            case MAIN:
                switch (buttonPressed) {

                    case 0:
                        logMetricGainGoldAndDamage("FaceTrader", "Touch", goldReward, damage);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                        AbstractDungeon.effectList.add(new RainingGoldEffect(goldReward));
                        AbstractDungeon.player.gainGold(goldReward);

                        AbstractDungeon.player.damage(new DamageInfo(null, damage));
                        CardCrawlGame.sound.play("ATTACK_POISON");
                        break;

                    case 1:
                        r = getRandomFace();
                        logMetricObtainRelic("FaceTrader", "Trade", r);
                        AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F,
                                r);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[3]);
                        break;

                    case 2:
                        logMetric("Leave");
                        this.imageEventText.updateBodyText(DESCRIPTIONS[4]);
                        break;
                }

                this.imageEventText.clearAllDialogs();
                this.imageEventText.setDialogOption(OPTIONS[3]);
                this.screen = CurScreen.RESULT;
                return;
        }
        openMap();
    }

    private AbstractRelic getRandomFace() {
        ArrayList<String> ids = new ArrayList<>();
        if (!AbstractDungeon.player.hasRelic("CultistMask")) {
            ids.add("CultistMask");
        }
        if (!AbstractDungeon.player.hasRelic("FaceOfCleric")) {
            ids.add("FaceOfCleric");
        }
        if (!AbstractDungeon.player.hasRelic("GremlinMask")) {
            ids.add("GremlinMask");
        }
        if (!AbstractDungeon.player.hasRelic("NlothsMask")) {
            ids.add("NlothsMask");
        }
        if (!AbstractDungeon.player.hasRelic("SsserpentHead")) {
            ids.add("SsserpentHead");
        }
        if (ids.size() <= 0) {
            ids.add("Circlet");
        }

        Collections.shuffle(ids, new Random(AbstractDungeon.miscRng.randomLong()));
        return RelicLibrary.getRelic(ids.get(0)).makeCopy();
    }

    public void logMetric(String actionTaken) {
        AbstractEvent.logMetric("FaceTrader", actionTaken);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\shrines\
 * FaceTrader.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

