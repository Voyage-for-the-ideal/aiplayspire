package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class AllOutAttack extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("All Out Attack");
    public static final String ID = "All Out Attack";

    public AllOutAttack() {
        super("All Out Attack", cardStrings.NAME, "green/attack/all_out_attack", 1, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 10;
        this.isMultiDamage = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                this.damageTypeForTurn, AbstractGameAction.AttackEffect.SLASH_HEAVY));
        addToBot((AbstractGameAction) new DiscardAction((AbstractCreature) p, (AbstractCreature) p, 1, true));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(4);
        }
    }

    public AbstractCard makeCopy() {
        return new AllOutAttack();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * AllOutAttack.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


