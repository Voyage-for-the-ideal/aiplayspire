package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.PotionStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;

public class ExplosivePotion extends AbstractPotion {
    public static final String POTION_ID = "Explosive Potion";
    private static final PotionStrings potionStrings = CardCrawlGame.languagePack.getPotionString("Explosive Potion");

    public ExplosivePotion() {
        super(potionStrings.NAME, "Explosive Potion", PotionRarity.COMMON, PotionSize.H, PotionColor.EXPLOSIVE);
        this.isThrown = true;
    }

    public void initializeData() {
        this.potency = getPotency();
        this.description = potionStrings.DESCRIPTIONS[0] + this.potency + potionStrings.DESCRIPTIONS[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
    }

    public void use(AbstractCreature target) {
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (!m.isDeadOrEscaped()) {
                addToBot((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new ExplosionSmallEffect(m.hb.cX, m.hb.cY), 0.1F));
            }
        }

        addToBot((AbstractGameAction) new WaitAction(0.5F));
        addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                DamageInfo.createDamageMatrix(this.potency, true), DamageInfo.DamageType.NORMAL,
                AbstractGameAction.AttackEffect.NONE));
    }

    public int getPotency(int ascensionLevel) {
        return 10;
    }

    public AbstractPotion makeCopy() {
        return new ExplosivePotion();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\potions\
 * ExplosivePotion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

