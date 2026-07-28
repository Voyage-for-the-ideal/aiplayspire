package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.actions.watcher.PressEndTurnButtonAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.CleaveEffect;

public class Conclude extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Conclude");
    public static final String ID = "Conclude";

    public Conclude() {
        super("Conclude", cardStrings.NAME, "purple/attack/conclude", 1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 12;
        this.isMultiDamage = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SFXAction("ATTACK_HEAVY"));
        addToBot((AbstractGameAction) new VFXAction((AbstractCreature) p, (AbstractGameEffect) new CleaveEffect(),
                0.1F));
        addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                this.damageTypeForTurn, AbstractGameAction.AttackEffect.NONE));
        addToBot((AbstractGameAction) new PressEndTurnButtonAction());
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(4);
        }
    }

    public AbstractCard makeCopy() {
        return new Conclude();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * Conclude.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

