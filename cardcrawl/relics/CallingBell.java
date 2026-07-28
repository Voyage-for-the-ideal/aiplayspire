package com.megacrit.cardcrawl.relics;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.curses.CurseOfTheBell;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class CallingBell
        extends AbstractRelic {
    public static final String ID = "Calling Bell";
    private boolean cardsReceived = true;

    public CallingBell() {
        super("Calling Bell", "bell.png", RelicTier.BOSS, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        this.cardsReceived = false;
        CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
        CurseOfTheBell curseOfTheBell = new CurseOfTheBell();
        UnlockTracker.markCardAsSeen(((AbstractCard) curseOfTheBell).cardID);
        group.addToBottom(curseOfTheBell.makeCopy());

        AbstractDungeon.gridSelectScreen.openConfirmationGrid(group, this.DESCRIPTIONS[1]);
        CardCrawlGame.sound.playA("BELL", MathUtils.random(-0.2F, -0.3F));
    }

    public void update() {
        super.update();
        if (!this.cardsReceived && !AbstractDungeon.isScreenUp) {
            AbstractDungeon.combatRewardScreen.open();
            AbstractDungeon.combatRewardScreen.rewards.clear();

            AbstractDungeon.combatRewardScreen.rewards.add(new RewardItem(
                    AbstractDungeon.returnRandomScreenlessRelic(RelicTier.COMMON)));
            AbstractDungeon.combatRewardScreen.rewards.add(new RewardItem(
                    AbstractDungeon.returnRandomScreenlessRelic(RelicTier.UNCOMMON)));
            AbstractDungeon.combatRewardScreen.rewards.add(new RewardItem(
                    AbstractDungeon.returnRandomScreenlessRelic(RelicTier.RARE)));

            AbstractDungeon.combatRewardScreen.positionRewards();
            AbstractDungeon.overlayMenu.proceedButton.setLabel(this.DESCRIPTIONS[2]);

            this.cardsReceived = true;
            (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.25F;
        }

        if (this.hb.hovered && InputHelper.justClickedLeft) {
            CardCrawlGame.sound.playA("BELL", MathUtils.random(-0.2F, -0.3F));
            flash();
        }
    }

    public AbstractRelic makeCopy() {
        return new CallingBell();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CallingBell
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

