package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class FruitJuice
        extends AbstractPotion {
    public static final String POTION_ID = "Fruit Juice";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Fruit Juice");

    public FruitJuice() {
        super(potionStrings.NAME, "Fruit Juice", PotionRarity.RARE, PotionSize.HEART, PotionColor.FRUIT);
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        AbstractDungeon.player.increaseMaxHp(this.potency, true);
    }

    public boolean canUse() {
        if (AbstractDungeon.actionManager.turnHasEnded &&
                (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            return false;
        }
        if ((AbstractDungeon.getCurrRoom()).event != null &&
                (AbstractDungeon.getCurrRoom()).event instanceof com.megacrit.cardcrawl.events.shrines.WeMeetAgain) {
            return false;
        }

        return true;
    }

    public int getPotency(int ascensionLevel) {
        return 5;
    }

    public AbstractPotion makeCopy() {
        return new FruitJuice();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\FruitJuice
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

