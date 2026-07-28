package com.megacrit.cardcrawl.events.exordium;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ShiningLight extends AbstractImageEvent {
    public static final String ID = "Shining Light";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Shining Light");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String INTRO = DESCRIPTIONS[0];
    private static final String AGREE_DIALOG = DESCRIPTIONS[1];
    private static final String DISAGREE_DIALOG = DESCRIPTIONS[2];

    private int damage = 0;

    private static final float HP_LOSS_PERCENT = 0.2F;
    private static final float A_2_HP_LOSS_PERCENT = 0.3F;
    private CUR_SCREEN screen = CUR_SCREEN.INTRO;

    private enum CUR_SCREEN {
        INTRO, COMPLETE;
    }

    public ShiningLight() {
        super(NAME, INTRO, "images/events/shiningLight.jpg");

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.damage = MathUtils.round(AbstractDungeon.player.maxHealth * 0.3F);
        } else {
            this.damage = MathUtils.round(AbstractDungeon.player.maxHealth * 0.2F);
        }

        if (AbstractDungeon.player.masterDeck.hasUpgradableCards().booleanValue()) {
            this.imageEventText.setDialogOption(OPTIONS[0] + this.damage + OPTIONS[1]);
        } else {
            this.imageEventText.setDialogOption(OPTIONS[3], true);
        }

        this.imageEventText.setDialogOption(OPTIONS[2]);
    }

    public void onEnterRoom() {
        if (Settings.AMBIANCE_ON) {
            CardCrawlGame.sound.play("EVENT_SHINING");
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO:
                if (buttonPressed == 0) {
                    this.imageEventText.updateBodyText(AGREE_DIALOG);
                    this.imageEventText.removeDialogOption(1);
                    this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                    this.screen = CUR_SCREEN.COMPLETE;
                    AbstractDungeon.player
                            .damage(new DamageInfo((AbstractCreature) AbstractDungeon.player, this.damage));
                    AbstractDungeon.effectList.add(new FlashAtkImgEffect(AbstractDungeon.player.hb.cX,
                            AbstractDungeon.player.hb.cY, AbstractGameAction.AttackEffect.FIRE));

                    upgradeCards();
                } else {
                    this.imageEventText.updateBodyText(DISAGREE_DIALOG);
                    this.imageEventText.removeDialogOption(1);
                    this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                    this.screen = CUR_SCREEN.COMPLETE;
                    AbstractEvent.logMetricIgnored("Shining Light");
                }
                return;
        }
        openMap();
    }

    private void upgradeCards() {
        AbstractDungeon.topLevelEffects.add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
        ArrayList<AbstractCard> upgradableCards = new ArrayList<>();
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.canUpgrade()) {
                upgradableCards.add(c);
            }
        }

        List<String> cardMetrics = new ArrayList<>();

        Collections.shuffle(upgradableCards, new Random(AbstractDungeon.miscRng.randomLong()));

        if (!upgradableCards.isEmpty()) {
            if (upgradableCards.size() == 1) {
                ((AbstractCard) upgradableCards.get(0)).upgrade();
                cardMetrics.add(((AbstractCard) upgradableCards.get(0)).cardID);
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
                AbstractDungeon.effectList.add(
                        new ShowCardBrieflyEffect(((AbstractCard) upgradableCards.get(0)).makeStatEquivalentCopy()));
            } else {
                ((AbstractCard) upgradableCards.get(0)).upgrade();
                ((AbstractCard) upgradableCards.get(1)).upgrade();
                cardMetrics.add(((AbstractCard) upgradableCards.get(0)).cardID);
                cardMetrics.add(((AbstractCard) upgradableCards.get(1)).cardID);
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(1));
                AbstractDungeon.effectList.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards

                        .get(0)).makeStatEquivalentCopy(), Settings.WIDTH / 2.0F - 190.0F * Settings.scale,
                        Settings.HEIGHT / 2.0F));

                AbstractDungeon.effectList.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards

                        .get(1)).makeStatEquivalentCopy(), Settings.WIDTH / 2.0F + 190.0F * Settings.scale,
                        Settings.HEIGHT / 2.0F));
            }
        }

        AbstractEvent.logMetric("Shining Light", "Entered Light", null, null, null, cardMetrics, null, null, null,
                this.damage, 0, 0, 0, 0, 0);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\exordium\
 * ShiningLight.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

