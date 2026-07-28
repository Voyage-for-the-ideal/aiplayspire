package com.megacrit.cardcrawl.events.exordium;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.curses.Parasite;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MonsterHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.OddMushroom;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Mushrooms extends AbstractEvent {
    private static final Logger logger = LogManager.getLogger(Mushrooms.class.getName());
    public static final String ID = "Mushrooms";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Mushrooms");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    public static final String ENC_NAME = "The Mushroom Lair";
    private Texture fgImg = ImageMaster.loadImage("images/events/fgShrooms.png");
    private Texture bgImg = ImageMaster.loadImage("images/events/bgShrooms.png");
    private static final float HEAL_AMT = 0.25F;
    private static final String HEAL_MSG = DESCRIPTIONS[0];
    private static final String FIGHT_MSG = DESCRIPTIONS[1];
    private int screenNum = 0;

    public Mushrooms() {
        this.body = DESCRIPTIONS[2];

        this.roomEventText.addDialogOption(OPTIONS[0]);
        int temp = (int) (AbstractDungeon.player.maxHealth * 0.25F);
        this.roomEventText.addDialogOption(OPTIONS[1] + temp + OPTIONS[2], CardLibrary.getCopy("Parasite"));
        (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.EVENT;
        this.hasDialog = true;
        this.hasFocus = true;
    }

    public void update() {
        super.update();
        if (!RoomEventDialog.waitForInput)
            buttonEffect(this.roomEventText.getSelectedOption());
    }

    protected void buttonEffect(int buttonPressed) {
        Parasite parasite;
        int healAmt;
        switch (buttonPressed) {

            case 0:
                if (this.screenNum == 0) {
                    (AbstractDungeon.getCurrRoom()).monsters = MonsterHelper.getEncounter("The Mushroom Lair");

                    this.roomEventText.updateBodyText(FIGHT_MSG);
                    this.roomEventText.updateDialogOption(0, OPTIONS[3]);
                    this.roomEventText.removeDialogOption(1);
                    AbstractEvent.logMetric("Mushrooms", "Fought Mushrooms");
                    this.screenNum += 2;
                } else if (this.screenNum == 1) {

                    openMap();
                } else if (this.screenNum == 2) {
                    if (Settings.isDailyRun) {
                        AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(25));
                    } else {
                        AbstractDungeon.getCurrRoom().addGoldToRewards(AbstractDungeon.miscRng.random(20, 30));
                    }

                    if (AbstractDungeon.player.hasRelic("Odd Mushroom")) {
                        AbstractDungeon.getCurrRoom().addRelicToRewards((AbstractRelic) new Circlet());
                    } else {
                        AbstractDungeon.getCurrRoom().addRelicToRewards((AbstractRelic) new OddMushroom());
                    }

                    enterCombat();
                    AbstractDungeon.lastCombatMetricKey = "The Mushroom Lair";
                }
                return;

            case 1:
                parasite = new Parasite();
                healAmt = (int) (AbstractDungeon.player.maxHealth * 0.25F);
                AbstractEvent.logMetricObtainCardAndHeal("Mushrooms", "Healed and dodged fight",
                        (AbstractCard) parasite, healAmt);
                AbstractDungeon.player.heal(healAmt);
                AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) parasite,
                        Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                this.roomEventText.updateBodyText(HEAL_MSG);
                this.roomEventText.updateDialogOption(0, OPTIONS[4]);
                this.roomEventText.removeDialogOption(1);
                this.screenNum = 1;
                return;
        }
        logger.info("ERROR: case " + buttonPressed + " should never be called");
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(this.bgImg, 0.0F, -10.0F, Settings.WIDTH, 1080.0F * Settings.scale);
        sb.draw(this.fgImg, 0.0F, -20.0F * Settings.scale, Settings.WIDTH, 1080.0F * Settings.scale);
    }

    public void dispose() {
        super.dispose();
        System.out.println("DISPOSING MUSHROOM ASSETS>");
        if (this.bgImg != null) {
            this.bgImg.dispose();
            this.bgImg = null;
        }
        if (this.fgImg != null) {
            this.fgImg.dispose();
            this.fgImg = null;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\exordium\
 * Mushrooms.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

