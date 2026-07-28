package com.megacrit.cardcrawl.events.city;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;

public class TheLibrary
        extends AbstractImageEvent {
    public static final String ID = "The Library";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("The Library");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private static final String DIALOG_1 = DESCRIPTIONS[0];
    private static final String SLEEP_RESULT = DESCRIPTIONS[1];
    private int screenNum = 0;

    private boolean pickCard = false;
    private static final float HP_HEAL_PERCENT = 0.33F;
    private static final float A_2_HP_HEAL_PERCENT = 0.2F;
    private int healAmt;

    public TheLibrary() {
        super(NAME, DIALOG_1, "images/events/library.jpg");

        if (AbstractDungeon.ascensionLevel >= 15) {
            this.healAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.2F);
        } else {
            this.healAmt = MathUtils.round(AbstractDungeon.player.maxHealth * 0.33F);
        }

        this.imageEventText.setDialogOption(OPTIONS[0]);
        this.imageEventText.setDialogOption(OPTIONS[1] + this.healAmt + OPTIONS[2]);
    }

    public void update() {
        super.update();
        if (this.pickCard &&
                !AbstractDungeon.isScreenUp && !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
            AbstractCard c = ((AbstractCard) AbstractDungeon.gridSelectScreen.selectedCards.get(0)).makeCopy();
            logMetricObtainCard("The Library", "Read", c);
            AbstractDungeon.effectList
                    .add(new ShowCardAndObtainEffect(c, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

            AbstractDungeon.gridSelectScreen.selectedCards.clear();
        }
    }

    protected void buttonEffect(int buttonPressed) {
        CardGroup group;
        int i;
        switch (this.screenNum) {

            case 0:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(getBook());
                        this.screenNum = 1;
                        this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                        this.imageEventText.clearRemainingOptions();
                        this.pickCard = true;
                        group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

                        for (i = 0; i < 20; i++) {
                            AbstractCard card = AbstractDungeon.getCard(AbstractDungeon.rollRarity()).makeCopy();

                            boolean containsDupe = true;
                            while (containsDupe) {
                                containsDupe = false;

                                for (AbstractCard c : group.group) {
                                    if (c.cardID.equals(card.cardID)) {
                                        containsDupe = true;
                                        card = AbstractDungeon.getCard(AbstractDungeon.rollRarity()).makeCopy();
                                    }
                                }
                            }

                            if (!group.contains(card)) {
                                for (AbstractRelic r : AbstractDungeon.player.relics) {
                                    r.onPreviewObtainCard(card);
                                }
                                group.addToBottom(card);
                            } else {
                                i--;
                            }
                        }

                        for (AbstractCard c : group.group) {
                            UnlockTracker.markCardAsSeen(c.cardID);
                        }
                        AbstractDungeon.gridSelectScreen.open(group, 1, OPTIONS[4], false);
                        return;
                }
                this.imageEventText.updateBodyText(SLEEP_RESULT);
                AbstractDungeon.player.heal(this.healAmt, true);
                logMetricHeal("The Library", "Heal", this.healAmt);
                this.screenNum = 1;
                this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                this.imageEventText.clearRemainingOptions();
                return;
        }

        openMap();
    }

    private String getBook() {
        ArrayList<String> list = new ArrayList<>();
        list.add(DESCRIPTIONS[2]);
        list.add(DESCRIPTIONS[3]);
        list.add(DESCRIPTIONS[4]);
        return list.get(MathUtils.random(2));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * TheLibrary.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

