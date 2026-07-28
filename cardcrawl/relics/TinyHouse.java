package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.vfx.UpgradeShineEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardBrieflyEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class TinyHouse extends AbstractRelic {
    public static final String ID = "Tiny House";
    private static final int GOLD_AMT = 50;
    private static final int HP_AMT = 5;

    public TinyHouse() {
        super("Tiny House", "tinyHouse.png", RelicTier.BOSS, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '2' + this.DESCRIPTIONS[1] + '\005' + this.DESCRIPTIONS[2];
    }

    public void onEquip() {
        ArrayList<AbstractCard> upgradableCards = new ArrayList<>();
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.canUpgrade()) {
                upgradableCards.add(c);
            }
        }
        Collections.shuffle(upgradableCards, new Random(AbstractDungeon.miscRng.randomLong()));

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
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(0));
                AbstractDungeon.player.bottledCardUpgradeCheck(upgradableCards.get(1));
                AbstractDungeon.topLevelEffects.add(new ShowCardBrieflyEffect(((AbstractCard) upgradableCards

                        .get(0)).makeStatEquivalentCopy(), Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                AbstractDungeon.topLevelEffects
                        .add(new UpgradeShineEffect(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
            }
        }

        AbstractDungeon.player.increaseMaxHp(5, true);

        AbstractDungeon.getCurrRoom().addGoldToRewards(50);
        AbstractDungeon.getCurrRoom().addPotionToRewards(PotionHelper.getRandomPotion(AbstractDungeon.miscRng));
        AbstractDungeon.combatRewardScreen.open(this.DESCRIPTIONS[3]);
        (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.0F;
    }

    public AbstractRelic makeCopy() {
        return new TinyHouse();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\TinyHouse.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

