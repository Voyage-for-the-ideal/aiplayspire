package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;

import java.util.ArrayList;
import java.util.Collections;

public class Test5
        extends AbstractRelic {
    public static final String ID = "Test 5";
    private static final int CARD_AMT = 2;

    public Test5() {
        super("Test 5", "test5.png", RelicTier.COMMON, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1];
    }

    public void onEquip() {
        ArrayList<AbstractCard> upgradableCards = new ArrayList<>();
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.canUpgrade() && c.type == AbstractCard.CardType.SKILL) {
                upgradableCards.add(c);
            }
        }
        Collections.shuffle(upgradableCards);

        if (!upgradableCards.isEmpty()) {
            if (upgradableCards.size() == 1) {
                ((AbstractCard) upgradableCards.get(0)).upgrade();
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
                AbstractDungeon.topLevelEffects.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards
                        .get(0)).makeStatEquivalentCopy()));
                AbstractDungeon.topLevelEffects
                        .add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
            } else {
                ((AbstractCard) upgradableCards.get(0)).upgrade();
                ((AbstractCard) upgradableCards.get(1)).upgrade();
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(1));
                AbstractDungeon.topLevelEffects.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards

                        .get(0)).makeStatEquivalentCopy(),
                        Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F - 20.0F * Settings.scale,
                        Settings.HEIGHT / 2.0F));

                AbstractDungeon.topLevelEffects.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards

                        .get(1)).makeStatEquivalentCopy(),
                        Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F + 20.0F * Settings.scale,
                        Settings.HEIGHT / 2.0F));

                AbstractDungeon.topLevelEffects
                        .add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new Test5();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Test5.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

