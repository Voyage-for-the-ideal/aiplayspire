package com.megacrit.cardcrawl.events.exordium;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.RainingGoldEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class GoopPuddle extends AbstractImageEvent {
    public static final String ID = "World of Goop";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("World of Goop");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final String GOLD_DIALOG = DESCRIPTIONS[1];
    private static final String LEAVE_DIALOG = DESCRIPTIONS[2];

    private CurScreen screen = CurScreen.INTRO;
    private int damage = 11;
    private int gold = 75;
    private int goldLoss;

    private enum CurScreen {
        INTRO, RESULT;
    }

    public GoopPuddle() {
        super(NAME, DIALOG_1, "images/events/goopPuddle.jpg");

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.goldLoss = AbstractDungeon.miscRng.random(35, 75);
        } else {
            this.goldLoss = AbstractDungeon.miscRng.random(20, 50);
        }

        if (this.goldLoss > AbstractDungeon.player.gold) {
            this.goldLoss = AbstractDungeon.player.gold;
        }
        this.imageEventText.setDialogOption(OPTIONS[0] + this.gold + OPTIONS[1] + this.damage + OPTIONS[2]);
        this.imageEventText.setDialogOption(OPTIONS[3] + this.goldLoss + OPTIONS[4]);
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_SPIRITS");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(GOLD_DIALOG);
                        this.imageEventText.clearAllDialogs();
                        AbstractDungeon.player
                                .damage(new DamageInfo((AbstractCreature) AbstractDungeon.player, this.damage));
                        AbstractDungeon.effectList.add(new FlashAtkImgEffect(AbstractDungeon.player.hb.cX,
                                AbstractDungeon.player.hb.cY, AbstractGameAction.AttackEffect.FIRE));

                        AbstractDungeon.effectList.add(new RainingGoldEffect(this.gold));
                        AbstractDungeon.player.gainGold(this.gold);
                        this.imageEventText.setDialogOption(OPTIONS[5]);
                        this.screen = CurScreen.RESULT;
                        AbstractEvent.logMetricGainGoldAndDamage("World of Goop", "Gather Gold", this.gold,
                                this.damage);
                        break;
                    case 1:
                        this.imageEventText.updateBodyText(LEAVE_DIALOG);
                        AbstractDungeon.player.loseGold(this.goldLoss);
                        this.imageEventText.clearAllDialogs();
                        this.imageEventText.setDialogOption(OPTIONS[5]);
                        this.screen = CurScreen.RESULT;
                        logMetricLoseGold("World of Goop", "Left Gold", this.goldLoss);
                        break;
                }
                return;
        }
        openMap();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\exordium\
 * GoopPuddle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

