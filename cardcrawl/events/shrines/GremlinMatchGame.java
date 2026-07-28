package com.megacrit.cardcrawl.events.shrines;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GremlinMatchGame
        extends AbstractImageEvent {
    public static final String ID = "Match and Keep!";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Match and Keep!");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private AbstractCard chosenCard;
    private AbstractCard hoveredCard;
    private int attemptCount = 5;
    private boolean cardFlipped = false;
    private boolean gameDone = false;
    private boolean cleanUpCalled = false;
    private CardGroup cards = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
    private float waitTimer = 0.0F;
    private int cardsMatched = 0;

    private CUR_SCREEN screen = CUR_SCREEN.INTRO;
    private static final String MSG_2 = DESCRIPTIONS[0];
    private static final String MSG_3 = DESCRIPTIONS[1];

    private List<String> matchedCards;

    public GremlinMatchGame() {
        super(NAME, DESCRIPTIONS[2], "images/events/matchAndKeep.jpg");
        this.cards.group = initializeCards();
        Collections.shuffle(this.cards.group, new Random(AbstractDungeon.miscRng.randomLong()));

        this.imageEventText.setDialogOption(OPTIONS[0]);
        this.matchedCards = new ArrayList<>();
    }

    private ArrayList<AbstractCard> initializeCards() {
        ArrayList<AbstractCard> retVal = new ArrayList<>();
        ArrayList<AbstractCard> retVal2 = new ArrayList<>();

        if (AbstractDungeon.ascensionLevel >= 15) {
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.RARE).makeCopy());
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.UNCOMMON).makeCopy());
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.COMMON).makeCopy());
            retVal.add(AbstractDungeon.returnRandomCurse());
            retVal.add(AbstractDungeon.returnRandomCurse());
        } else {
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.RARE).makeCopy());
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.UNCOMMON).makeCopy());
            retVal.add(AbstractDungeon.getCard(AbstractCard.CardRarity.COMMON).makeCopy());
            retVal.add(AbstractDungeon.returnColorlessCard(AbstractCard.CardRarity.UNCOMMON).makeCopy());
            retVal.add(AbstractDungeon.returnRandomCurse());
        }

        retVal.add(AbstractDungeon.player.getStartCardForEvent());

        for (AbstractCard c : retVal) {
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onPreviewObtainCard(c);
            }
            retVal2.add(c.makeStatEquivalentCopy());
        }

        retVal.addAll(retVal2);

        for (AbstractCard c : retVal) {
            c.current_x = Settings.WIDTH / 2.0F;
            c.target_x = c.current_x;
            c.current_y = -300.0F * Settings.scale;
            c.target_y = c.current_y;
        }

        return retVal;
    }

    private enum CUR_SCREEN {
        INTRO, RULE_EXPLANATION, PLAY, COMPLETE, CLEAN_UP;
    }

    public void update() {
        super.update();
        this.cards.update();

        if (this.screen == CUR_SCREEN.PLAY) {
            updateControllerInput();
            updateMatchGameLogic();
        } else if (this.screen == CUR_SCREEN.CLEAN_UP) {
            if (!this.cleanUpCalled) {
                this.cleanUpCalled = true;
                cleanUpCards();
            }
            if (this.waitTimer > 0.0F) {
                this.waitTimer -= Gdx.graphics.getDeltaTime();
                if (this.waitTimer < 0.0F) {
                    this.waitTimer = 0.0F;
                    this.screen = CUR_SCREEN.COMPLETE;
                    GenericEventDialog.show();
                    this.imageEventText.updateBodyText(MSG_3);
                    this.imageEventText.clearRemainingOptions();
                    this.imageEventText.setDialogOption(OPTIONS[1]);
                }
            }
        }

        if (!GenericEventDialog.waitForInput) {
            buttonEffect(GenericEventDialog.getSelectedOption());
        }
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        boolean anyHovered = false;
        int index = 0;
        for (AbstractCard c : this.cards.group) {
            if (c.hb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) this.cards.group.get(0)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) this.cards.group.get(0)).hb.cY);
        } else {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                float y = ((AbstractCard) this.cards.group.get(index)).hb.cY + 230.0F * Settings.scale;
                if (y > 865.0F * Settings.scale) {
                    y = 290.0F * Settings.scale;
                }
                Gdx.input.setCursorPosition((int) ((AbstractCard) this.cards.group.get(index)).hb.cX,
                        (int) (Settings.HEIGHT - y));
            } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                float y = ((AbstractCard) this.cards.group.get(index)).hb.cY - 230.0F * Settings.scale;
                if (y < 175.0F * Settings.scale) {
                    y = 750.0F * Settings.scale;
                }
                Gdx.input.setCursorPosition((int) ((AbstractCard) this.cards.group.get(index)).hb.cX,
                        (int) (Settings.HEIGHT - y));
            } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                float x = ((AbstractCard) this.cards.group.get(index)).hb.cX - 210.0F * Settings.scale;
                if (x < 530.0F * Settings.scale) {
                    x = 1270.0F * Settings.scale;
                }
                Gdx.input.setCursorPosition((int) x,
                        Settings.HEIGHT - (int) ((AbstractCard) this.cards.group.get(index)).hb.cY);
            } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                float x = ((AbstractCard) this.cards.group.get(index)).hb.cX + 210.0F * Settings.scale;
                if (x > 1375.0F * Settings.scale) {
                    x = 640.0F * Settings.scale;
                }
                Gdx.input.setCursorPosition((int) x,
                        Settings.HEIGHT - (int) ((AbstractCard) this.cards.group.get(index)).hb.cY);
            }

            if (CInputActionSet.select.isJustPressed()) {
                CInputActionSet.select.unpress();
                InputHelper.justClickedLeft = true;
            }
        }
    }

    private void cleanUpCards() {
        for (AbstractCard c : this.cards.group) {
            c.targetDrawScale = 0.5F;
            c.target_x = Settings.WIDTH / 2.0F;
            c.target_y = -300.0F * Settings.scale;
        }
    }

    private void updateMatchGameLogic() {
        if (this.waitTimer == 0.0F) {
            this.hoveredCard = null;
            for (AbstractCard c : this.cards.group) {
                c.hb.update();
                if (this.hoveredCard == null && c.hb.hovered) {
                    c.drawScale = 0.7F;
                    c.targetDrawScale = 0.7F;
                    this.hoveredCard = c;
                    if (InputHelper.justClickedLeft && this.hoveredCard.isFlipped) {
                        InputHelper.justClickedLeft = false;
                        this.hoveredCard.isFlipped = false;
                        if (!this.cardFlipped) {
                            this.cardFlipped = true;
                            this.chosenCard = this.hoveredCard;
                            continue;
                        }
                        this.cardFlipped = false;
                        if (this.chosenCard.cardID.equals(this.hoveredCard.cardID)) {
                            this.waitTimer = 1.0F;
                            this.chosenCard.targetDrawScale = 0.7F;
                            this.chosenCard.target_x = Settings.WIDTH / 2.0F;
                            this.chosenCard.target_y = Settings.HEIGHT / 2.0F;
                            this.hoveredCard.targetDrawScale = 0.7F;
                            this.hoveredCard.target_x = Settings.WIDTH / 2.0F;
                            this.hoveredCard.target_y = Settings.HEIGHT / 2.0F;
                            continue;
                        }
                        this.waitTimer = 1.25F;
                        this.chosenCard.targetDrawScale = 1.0F;
                        this.hoveredCard.targetDrawScale = 1.0F;
                    }

                    continue;
                }
                if (c != this.chosenCard) {
                    c.targetDrawScale = 0.5F;
                }
            }
        } else {

            this.waitTimer -= Gdx.graphics.getDeltaTime();
            if (this.waitTimer < 0.0F && !this.gameDone) {
                this.waitTimer = 0.0F;

                if (this.chosenCard.cardID.equals(this.hoveredCard.cardID)) {
                    this.cardsMatched++;
                    this.cards.group.remove(this.chosenCard);
                    this.cards.group.remove(this.hoveredCard);
                    this.matchedCards.add(this.chosenCard.cardID);
                    AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(this.chosenCard
                            .makeCopy(), Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
                    this.chosenCard = null;
                    this.hoveredCard = null;
                } else {
                    this.chosenCard.isFlipped = true;
                    this.hoveredCard.isFlipped = true;
                    this.chosenCard.targetDrawScale = 0.5F;
                    this.hoveredCard.targetDrawScale = 0.5F;
                    this.chosenCard = null;
                    this.hoveredCard = null;
                }
                this.attemptCount--;
                if (this.attemptCount == 0) {
                    this.gameDone = true;
                    this.waitTimer = 1.0F;
                }
            } else if (this.gameDone) {

                this.screen = CUR_SCREEN.CLEAN_UP;
            }
        }
    }

    protected void buttonEffect(int buttonPressed) {
        switch (this.screen) {
            case INTRO:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.updateBodyText(MSG_2);
                        this.imageEventText.updateDialogOption(0, OPTIONS[2]);
                        this.screen = CUR_SCREEN.RULE_EXPLANATION;
                        break;
                }

                break;
            case RULE_EXPLANATION:
                switch (buttonPressed) {
                    case 0:
                        this.imageEventText.removeDialogOption(0);
                        GenericEventDialog.hide();
                        this.screen = CUR_SCREEN.PLAY;
                        placeCards();
                        break;
                }

                break;
            case COMPLETE:
                logMetricObtainCards("Match and Keep!", this.cardsMatched + " cards matched", this.matchedCards);
                openMap();
                break;
        }
    }

    private void placeCards() {
        for (int i = 0; i < this.cards.size(); i++) {
            ((AbstractCard) this.cards.group.get(i)).target_x = (i % 4) * 210.0F * Settings.xScale
                    + 640.0F * Settings.xScale;
            ((AbstractCard) this.cards.group.get(i)).target_y = (i % 3) * -230.0F * Settings.yScale
                    + 750.0F * Settings.yScale;
            ((AbstractCard) this.cards.group.get(i)).targetDrawScale = 0.5F;
            ((AbstractCard) this.cards.group.get(i)).isFlipped = true;
        }
    }

    public void render(SpriteBatch sb) {
        this.cards.render(sb);
        if (this.chosenCard != null) {
            this.chosenCard.render(sb);
        }
        if (this.hoveredCard != null) {
            this.hoveredCard.render(sb);
        }

        if (this.screen == CUR_SCREEN.PLAY)
            FontHelper.renderSmartText(sb, FontHelper.panelNameFont, OPTIONS[3] + this.attemptCount,
                    780.0F * Settings.scale, 80.0F * Settings.scale, 2000.0F * Settings.scale, 0.0F, Color.WHITE);
    }

    public void renderAboveTopPanel(SpriteBatch sb) {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\shrines\
 * GremlinMatchGame.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

