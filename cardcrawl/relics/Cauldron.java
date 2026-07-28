package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.rewards.RewardItem;

public class Cauldron extends AbstractRelic {
    public static final String ID = "Cauldron";

    public Cauldron() {
        super("Cauldron", "cauldron.png", RelicTier.SHOP, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        for (int i = 0; i < 5; i++) {
            AbstractDungeon.getCurrRoom().addPotionToRewards(PotionHelper.getRandomPotion());
        }

        AbstractDungeon.combatRewardScreen.open(this.DESCRIPTIONS[1]);
        (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.0F;

        int remove = -1;
        for (int j = 0; j < AbstractDungeon.combatRewardScreen.rewards.size(); j++) {
            if (((RewardItem) AbstractDungeon.combatRewardScreen.rewards.get(j)).type == RewardItem.RewardType.CARD) {
                remove = j;
                break;
            }
        }
        if (remove != -1) {
            AbstractDungeon.combatRewardScreen.rewards.remove(remove);
        }
    }

    public AbstractRelic makeCopy() {
        return new Cauldron();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Cauldron.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

