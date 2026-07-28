package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.SmokeBombEffect;

public class SmokeBomb extends AbstractPotion {
    public static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("SmokeBomb");

    public static final String POTION_ID = "SmokeBomb";

    public SmokeBomb() {
        super(potionStrings.NAME, "SmokeBomb", PotionRarity.RARE, PotionSize.SPHERE, PotionColor.SMOKE);
        this.description = potionStrings.DESCRIPTIONS[0];
        this.isThrown = true;
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            (AbstractDungeon.getCurrRoom()).smoked = true;
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new SmokeBombEffect(
                    ((AbstractCreature) abstractPlayer).hb.cX, ((AbstractCreature) abstractPlayer).hb.cY)));
            AbstractDungeon.player.hideHealthBar();
            AbstractDungeon.player.isEscaping = true;
            AbstractDungeon.player.flipHorizontal = !AbstractDungeon.player.flipHorizontal;
            AbstractDungeon.overlayMenu.endTurnButton.disable();
            AbstractDungeon.player.escapeTimer = 2.5F;
        }
    }

    public boolean canUse() {
        if (super.canUse()) {
            for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                if (m.hasPower("BackAttack")) {
                    return false;
                }
                if (m.type == AbstractMonster.EnemyType.BOSS) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public int getPotency(int ascensionLevel) {
        return 0;
    }

    public AbstractPotion makeCopy() {
        return new SmokeBomb();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\SmokeBomb.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

