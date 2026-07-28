package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.cards.curses.Necronomicurse;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

public class Necronomicon extends AbstractRelic {
    public static final String ID = "Necronomicon";
    private static final int COST_THRESHOLD = 2;
    private boolean activated = true;

    public Necronomicon() {
        super("Necronomicon", "necronomicon.png", RelicTier.SPECIAL, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1];
    }

    public void onEquip() {
        CardCrawlGame.sound.play("NECRONOMICON");
        this.description = this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[2];
        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) new Necronomicurse(),
                Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

        UnlockTracker.markCardAsSeen("Necronomicurse");
    }

    public void onUnequip() {
        AbstractCard cardToRemove = null;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c instanceof Necronomicurse) {
                cardToRemove = c;

                break;
            }
        }
        if (cardToRemove != null) {
            AbstractDungeon.player.masterDeck.group.remove(cardToRemove);
        }
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK
                && ((card.costForTurn >= 2 && !card.freeToPlayOnce) || (card.cost == -1 && card.energyOnUse >= 2))
                && this.activated) {

            this.activated = false;
            flash();
            AbstractMonster m = null;

            if (action.target != null) {
                m = (AbstractMonster) action.target;
            }

            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            AbstractCard tmp = card.makeSameInstanceOf();
            tmp.current_x = card.current_x;
            tmp.current_y = card.current_y;
            tmp.target_x = Settings.WIDTH / 2.0F - 300.0F * Settings.scale;
            tmp.target_y = Settings.HEIGHT / 2.0F;
            tmp.applyPowers();
            tmp.purgeOnUse = true;
            AbstractDungeon.actionManager.addCardQueueItem(new CardQueueItem(tmp, m, card.energyOnUse, true, true),
                    true);

            this.pulse = false;
        }
    }

    public void atTurnStart() {
        this.activated = true;
    }

    public boolean checkTrigger() {
        return this.activated;
    }

    public AbstractRelic makeCopy() {
        return new Necronomicon();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * Necronomicon.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

