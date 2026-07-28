package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.MetallicizePower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class HeartOfIron extends AbstractPotion {
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("HeartOfIron");
    public static final String POTION_ID = "HeartOfIron";

    public HeartOfIron() {
        super(potionStrings.NAME, "HeartOfIron", PotionRarity.RARE, PotionSize.HEART, PotionColor.SWIFT);
        this.labOutlineColor = Settings.RED_RELIC_COLOR;
        this.isThrown = false;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new MetallicizePower((AbstractCreature) abstractPlayer, this.potency),
                    this.potency));
        }
    }

    public int getPotency(int ascensionLevel) {
        return 6;
    }

    public AbstractPotion makeCopy() {
        return new HeartOfIron();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * HeartOfIron.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

