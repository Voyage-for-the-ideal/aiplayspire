package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;

import java.util.ArrayList;

public class ExhaustPileViewScreen
        implements ScrollBarListener {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("ExhaustViewScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    private CardGroup exhaustPileCopy = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
    public boolean isHovered = false;
    private static final int CARDS_PER_LINE = 5;
    private boolean grabbedScreen = false;
    private float grabStartY = 0.0F;
    private float currentDiffY = 0.0F;
    private static float drawStartX;
    private static float drawStartY;
    private float scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    private static float padX;
    private static float padY;
    private float scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
    private static final String DESC = TEXT[0];
    private AbstractCard hoveredCard = null;
    private int prevDeckSize = 0;
    private static final float SCROLL_BAR_THRESHOLD = 500.0F * Settings.scale;

    private ScrollBar scrollBar;
    private AbstractCard controllerCard = null;

    public ExhaustPileViewScreen() {
        drawStartX = Settings.WIDTH;
        drawStartX -= 5.0F * AbstractCard.IMG_WIDTH * 0.75F;
        drawStartX -= 4.0F * Settings.CARD_VIEW_PAD_X;
        drawStartX /= 2.0F;
        drawStartX += AbstractCard.IMG_WIDTH * 0.75F / 2.0F;

        padX = AbstractCard.IMG_WIDTH * 0.75F + Settings.CARD_VIEW_PAD_X;
        padY = AbstractCard.IMG_HEIGHT * 0.75F + Settings.CARD_VIEW_PAD_Y;

        this.scrollBar = new ScrollBar(this);
        this.scrollBar.move(0.0F, -30.0F * Settings.scale);
    }

    public void update() {
        boolean isDraggingScrollBar = false;
        if (shouldShowScrollBar()) {
            isDraggingScrollBar = this.scrollBar.update();
        }
        if (!isDraggingScrollBar) {
            updateScrolling();
        }
        if (this.exhaustPileCopy.group.size() > 0) {
            updateControllerInput();
        }
        if (Settings.isControllerMode && this.controllerCard != null && !CardCrawlGame.isPopupOpen &&
                !CInputHelper.isTopPanelActive()) {
            if (Gdx.input.getY() > Settings.HEIGHT * 0.7F) {
                this.currentDiffY += Settings.SCROLL_SPEED;
            } else if (Gdx.input.getY() < Settings.HEIGHT * 0.3F) {
                this.currentDiffY -= Settings.SCROLL_SPEED;
            }
        }

        updatePositions();

        if (Settings.isControllerMode && this.controllerCard != null && !CInputHelper.isTopPanelActive()) {
            CInputHelper.setCursor(this.controllerCard.hb);
        }
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode || CInputHelper.isTopPanelActive()) {
            return;
        }

        boolean anyHovered = false;
        int index = 0;

        for (AbstractCard c : this.exhaustPileCopy.group) {
            if (c.hb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            CInputHelper.setCursor(((AbstractCard) this.exhaustPileCopy.group.get(0)).hb);
            this.controllerCard = this.exhaustPileCopy.group.get(0);
        } else if ((CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) && this.exhaustPileCopy
                .size() > 5) {

            index -= 5;
            if (index < 0) {
                int wrap = this.exhaustPileCopy.size() / 5;
                index += wrap * 5;
                if (index + 5 < this.exhaustPileCopy.size()) {
                    index += 5;
                }
            }
            CInputHelper.setCursor(((AbstractCard) this.exhaustPileCopy.group.get(index)).hb);
            this.controllerCard = this.exhaustPileCopy.group.get(index);
        } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed())
                && this.exhaustPileCopy
                        .size() > 5) {
            if (index < this.exhaustPileCopy.size() - 5) {
                index += 5;
            } else {
                index %= 5;
            }
            CInputHelper.setCursor(((AbstractCard) this.exhaustPileCopy.group.get(index)).hb);
            this.controllerCard = this.exhaustPileCopy.group.get(index);
        } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            if (index % 5 > 0) {
                index--;
            } else {
                index += 4;
                if (index > this.exhaustPileCopy.size() - 1) {
                    index = this.exhaustPileCopy.size() - 1;
                }
            }
            CInputHelper.setCursor(((AbstractCard) this.exhaustPileCopy.group.get(index)).hb);
            this.controllerCard = this.exhaustPileCopy.group.get(index);
        } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            if (index % 5 < 4) {
                index++;
                if (index > this.exhaustPileCopy.size() - 1) {
                    index -= this.exhaustPileCopy.size() % 5;
                }
            } else {
                index -= 4;
                if (index < 0) {
                    index = 0;
                }
            }
            CInputHelper.setCursor(((AbstractCard) this.exhaustPileCopy.group.get(index)).hb);
            this.controllerCard = this.exhaustPileCopy.group.get(index);
        }
    }

    private void updateScrolling() {
        int y = InputHelper.mY;

        if (!this.grabbedScreen) {
            if (InputHelper.scrolledDown) {
                this.currentDiffY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                this.currentDiffY -= Settings.SCROLL_SPEED;
            }

            if (InputHelper.justClickedLeft) {
                this.grabbedScreen = true;
                this.grabStartY = y - this.currentDiffY;
            }

        } else if (InputHelper.isMouseDown) {
            this.currentDiffY = y - this.grabStartY;
        } else {
            this.grabbedScreen = false;
        }

        if (this.prevDeckSize != this.exhaustPileCopy.size()) {
            calculateScrollBounds();
        }
        resetScrolling();
        updateBarPosition();
    }

    private void calculateScrollBounds() {
        if (this.exhaustPileCopy.size() > 10) {
            int scrollTmp = this.exhaustPileCopy.size() / 5 - 2;
            if (this.exhaustPileCopy.size() % 5 != 0) {
                scrollTmp++;
            }
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT + scrollTmp * padY;
        } else {
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
        }

        this.prevDeckSize = this.exhaustPileCopy.size();
    }

    private void resetScrolling() {
        if (this.currentDiffY < this.scrollLowerBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollLowerBound);
        } else if (this.currentDiffY > this.scrollUpperBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollUpperBound);
        }
    }

    private void updatePositions() {
        this.hoveredCard = null;
        int lineNum = 0;
        ArrayList<AbstractCard> cards = this.exhaustPileCopy.group;
        for (int i = 0; i < cards.size(); i++) {
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }
            ((AbstractCard) cards.get(i)).target_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).target_y = drawStartY + this.currentDiffY - lineNum * padY;
            ((AbstractCard) cards.get(i)).update();

            if (AbstractDungeon.topPanel.potionUi.isHidden) {
                ((AbstractCard) cards.get(i)).updateHoverLogic();
                if (((AbstractCard) cards.get(i)).hb.hovered) {
                    this.hoveredCard = cards.get(i);
                }
            }
        }
    }

    public void reopen() {
        AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
    }

    public void open() {
        CardCrawlGame.sound.play("DECK_OPEN");
        AbstractDungeon.overlayMenu.showBlackScreen();
        AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
        this.currentDiffY = 0.0F;
        this.grabStartY = 0.0F;
        this.grabbedScreen = false;
        AbstractDungeon.isScreenUp = true;
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.EXHAUST_VIEW;
        this.exhaustPileCopy.clear();

        for (AbstractCard c : AbstractDungeon.player.exhaustPile.group) {
            AbstractCard toAdd = c.makeStatEquivalentCopy();
            toAdd.setAngle(0.0F, true);
            toAdd.targetDrawScale = 0.75F;
            toAdd.targetDrawScale = 0.75F;
            toAdd.drawScale = 0.75F;
            toAdd.lighten(true);
            this.exhaustPileCopy.addToBottom(toAdd);
        }

        if (!AbstractDungeon.player.hasRelic("Frozen Eye")) {
            this.exhaustPileCopy.sortAlphabetically(true);
            this.exhaustPileCopy.sortByRarityPlusStatusCardType(true);
        }
        hideCards();

        if (this.exhaustPileCopy.group.size() <= 5) {
            drawStartY = Settings.HEIGHT * 0.5F;
        } else {
            drawStartY = Settings.HEIGHT * 0.66F;
        }

        calculateScrollBounds();
    }

    private void hideCards() {
        int lineNum = 0;
        ArrayList<AbstractCard> cards = this.exhaustPileCopy.group;
        for (int i = 0; i < cards.size(); i++) {
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }
            ((AbstractCard) cards.get(i)).current_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).current_y = drawStartY + this.currentDiffY - lineNum * padY
                    - MathUtils.random(100.0F * Settings.scale, 200.0F * Settings.scale);

            ((AbstractCard) cards.get(i)).targetDrawScale = 0.75F;
            ((AbstractCard) cards.get(i)).drawScale = 0.75F;
        }
    }

    public void render(SpriteBatch sb) {
        if (shouldShowScrollBar()) {
            this.scrollBar.render(sb);
        }

        if (this.hoveredCard == null) {
            this.exhaustPileCopy.render(sb);
        } else {
            this.exhaustPileCopy.renderExceptOneCard(sb, this.hoveredCard);
            this.hoveredCard.renderHoverShadow(sb);
            this.hoveredCard.render(sb);
            this.hoveredCard.renderCardTip(sb);
        }

        FontHelper.renderDeckViewTip(sb, DESC, 96.0F * Settings.scale, Settings.CREAM_COLOR);
    }

    public void scrolledUsingBar(float newPercent) {
        this.currentDiffY = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound,
                newPercent);
        updateBarPosition();
    }

    private void updateBarPosition() {
        float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound,
                this.currentDiffY);
        this.scrollBar.parentScrolledToPercent(percent);
    }

    private boolean shouldShowScrollBar() {
        return (this.scrollUpperBound > SCROLL_BAR_THRESHOLD);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * ExhaustPileViewScreen.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

