package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rewards.RewardItem;

public class ToxicEgg2 extends AbstractRelic {
    public static final String ID = "Toxic Egg 2";

    public ToxicEgg2() {
        super("Toxic Egg 2", "toxicEgg.png", RelicTier.UNCOMMON, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            if (reward.cards != null) {
                for (AbstractCard c : reward.cards) {
                    onPreviewObtainCard(c);
                }
            }
        }
    }

    public void onPreviewObtainCard(AbstractCard c) {
        onObtainCard(c);
    }

    public void onObtainCard(AbstractCard c) {
        if (c.type == AbstractCard.CardType.SKILL && c.canUpgrade() && !c.upgraded) {
            c.upgrade();
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new ToxicEgg2();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\ToxicEgg2.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

