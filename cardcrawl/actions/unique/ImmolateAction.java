package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ExhaustSpecificCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class ImmolateAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ImmolateAction");
    public static final String[] TEXT = uiStrings.TEXT;

    public int[] damage;

    public ImmolateAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type) {
        setValues(null, source, amount[0]);
        this.damage = amount;
        this.actionType = ActionType.DAMAGE;
        this.damageType = type;
        this.attackEffect = AttackEffect.FIRE;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            if (AbstractDungeon.player.hand.size() == 0) {
                this.isDone = true;

                return;
            }
            if (AbstractDungeon.player.hand.size() == 1) {
                AbstractCard card = AbstractDungeon.player.hand.getBottomCard();
                if (card.type == AbstractCard.CardType.CURSE || card.type == AbstractCard.CardType.STATUS) {
                    dealDamage();
                }
                addToTop((AbstractGameAction) new ExhaustSpecificCardAction(card, AbstractDungeon.player.hand));
                this.isDone = true;

                return;
            }
            AbstractDungeon.handCardSelectScreen.open(TEXT[0], 1, false);
            tickDuration();

            return;
        }

        if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            for (AbstractCard c : AbstractDungeon.handCardSelectScreen.selectedCards.group) {
                if (c.type == AbstractCard.CardType.CURSE || c.type == AbstractCard.CardType.STATUS) {
                    dealDamage();
                }
                addToTop((AbstractGameAction) new ExhaustSpecificCardAction(c,
                        AbstractDungeon.handCardSelectScreen.selectedCards));
            }
            AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
        }

        tickDuration();
    }

    public void dealDamage() {
        boolean playedMusic = false;
        int temp = (AbstractDungeon.getCurrRoom()).monsters.monsters.size();
        for (int i = 0; i < temp; i++) {
            if (!((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isDying &&
                    !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isEscaping) {
                if (playedMusic) {
                    AbstractDungeon.effectList.add(new FlashAtkImgEffect(

                            ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cX,
                            ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cY,
                            this.attackEffect, true));
                } else {

                    playedMusic = true;
                    AbstractDungeon.effectList.add(new FlashAtkImgEffect(

                            ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cX,
                            ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cY,
                            this.attackEffect));
                }
            }
        }

        for (AbstractPower p : AbstractDungeon.player.powers) {
            p.onDamageAllEnemies(this.damage);
        }

        int temp2 = (AbstractDungeon.getCurrRoom()).monsters.monsters.size();
        for (int j = 0; j < temp2; j++) {
            if (!((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(j)).isDying &&
                    !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(j)).isEscaping) {
                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(j)).tint.color = Color.RED
                        .cpy();
                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(j)).tint
                        .changeColor(Color.WHITE.cpy());
                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(j))
                        .damage(new DamageInfo(this.source, this.damage[j], this.damageType));
            }
        }

        if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead())
            AbstractDungeon.actionManager.clearPostCombatActions();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * ImmolateAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



