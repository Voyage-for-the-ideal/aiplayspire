package com.megacrit.cardcrawl.potions;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ObtainPotionAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.ObtainPotionEffect;

public class EntropicBrew
        extends AbstractPotion {
    public static final String POTION_ID = "EntropicBrew";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("EntropicBrew");

    public EntropicBrew() {
        super(potionStrings.NAME, "EntropicBrew", PotionRarity.RARE, PotionSize.M, PotionEffect.RAINBOW, Color.WHITE,
                null, null);

        this.description = potionStrings.DESCRIPTIONS[0];
        this.potency = getPotency();
        this.isThrown = false;
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            for (int i = 0; i < AbstractDungeon.player.potionSlots; i++) {
                addToBot((AbstractGameAction) new ObtainPotionAction(AbstractDungeon.returnRandomPotion(true)));
            }
        } else if (AbstractDungeon.player.hasRelic("Sozu")) {
            AbstractDungeon.player.getRelic("Sozu").flash();
        } else {
            for (int i = 0; i < AbstractDungeon.player.potionSlots; i++) {
                AbstractDungeon.effectsQueue.add(new ObtainPotionEffect(AbstractDungeon.returnRandomPotion()));
            }
        }
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
        if (AbstractDungeon.player != null) {
            return AbstractDungeon.player.potionSlots;
        }
        return 3;
    }

    public AbstractPotion makeCopy() {
        return new EntropicBrew();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * EntropicBrew.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

