package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.GameDictionary;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.util.ArrayList;
import java.util.Iterator;

public class PandorasBox
        extends AbstractRelic {
    public static final String ID = "Pandora's Box";
    private int count = 0;
    private boolean calledTransform = true;

    public PandorasBox() {
        super("Pandora's Box", "pandoras_box.png", RelicTier.BOSS, LandingSound.MAGICAL);
        removeStrikeTip();
    }

    private void removeStrikeTip() {
        ArrayList<String> strikes = new ArrayList<>();

        for (String s : GameDictionary.STRIKE.NAMES) {
            strikes.add(s.toLowerCase());
        }

        for (Iterator<PowerTip> t = this.tips.iterator(); t.hasNext();) {
            PowerTip derp = t.next();
            String s = derp.header.toLowerCase();
            if (strikes.contains(s)) {
                t.remove();
                break;
            }
        }
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        this.calledTransform = false;

        for (Iterator<AbstractCard> i = AbstractDungeon.player.masterDeck.group.iterator(); i.hasNext();) {
            AbstractCard e = i.next();
            if (e.hasTag(AbstractCard.CardTags.STARTER_DEFEND) || e.hasTag(AbstractCard.CardTags.STARTER_STRIKE)) {
                i.remove();
                this.count++;
            }
        }

        if (this.count > 0) {
            CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
            for (int j = 0; j < this.count; j++) {
                AbstractCard card = AbstractDungeon.returnTrulyRandomCard().makeCopy();
                UnlockTracker.markCardAsSeen(card.cardID);
                card.isSeen = true;
                for (AbstractRelic r : AbstractDungeon.player.relics) {
                    r.onPreviewObtainCard(card);
                }
                group.addToBottom(card);
            }
            AbstractDungeon.gridSelectScreen.openConfirmationGrid(group, this.DESCRIPTIONS[1]);
        }
    }

    public void update() {
        super.update();
        if (!this.calledTransform && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.GRID) {
            this.calledTransform = true;
            (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.25F;
        }
    }

    public AbstractRelic makeCopy() {
        return new PandorasBox();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\PandorasBox
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

