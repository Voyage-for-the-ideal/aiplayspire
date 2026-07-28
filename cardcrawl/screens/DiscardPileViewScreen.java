package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;

import java.util.ArrayList;

public class DiscardPileViewScreen
        implements ScrollBarListener {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("DiscardPileViewScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    public boolean isHovered = false;

    private static final int CARDS_PER_LINE = 5;
    private boolean grabbedScreen = false;
    private static final float SCROLL_BAR_THRESHOLD = 500.0F * Settings.scale;
    private static float drawStartX;
    private static float drawStartY;
    private static float padX;
    private static float padY;
    private float scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    private float scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
    private float grabStartY = this.scrollLowerBound;
    private float currentDiffY = this.scrollLowerBound;
    private static final String HEADER_INFO = TEXT[0];
    private AbstractCard hoveredCard = null;
    private int prevDeckSize = 0;
    private ScrollBar scrollBar;
    private AbstractCard controllerCard = null;

    public DiscardPileViewScreen() {
        drawStartX = Settings.WIDTH;
        drawStartX -= 5.0F * AbstractCard.IMG_WIDTH * 0.75F;
        drawStartX -= 4.0F * Settings.CARD_VIEW_PAD_X;
        drawStartX /= 2.0F;
        drawStartX += AbstractCard.IMG_WIDTH * 0.75F / 2.0F;

        padX = AbstractCard.IMG_WIDTH * 0.75F + Settings.CARD_VIEW_PAD_X;
        padY = AbstractCard.IMG_HEIGHT * 0.75F + Settings.CARD_VIEW_PAD_Y;
        this.scrollBar = new ScrollBar(this);
        this.scrollBar.changeHeight(Settings.HEIGHT - 384.0F * Settings.scale);
    }

    public void update() {
        updateControllerInput();
        if (Settings.isControllerMode && this.controllerCard != null && !CardCrawlGame.isPopupOpen &&
                !CInputHelper.isTopPanelActive()) {
            if (Gdx.input.getY() > Settings.HEIGHT * 0.7F) {
                this.currentDiffY += Settings.SCROLL_SPEED;
            } else if (Gdx.input.getY() < Settings.HEIGHT * 0.3F) {
                this.currentDiffY -= Settings.SCROLL_SPEED;
            }
        }

        boolean isDraggingScrollBar = false;
        if (shouldShowScrollBar()) {
            isDraggingScrollBar = this.scrollBar.update();
        }
        if (!isDraggingScrollBar) {
            updateScrolling();
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

        for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
            if (c.hb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(0)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(0)).hb.cY);
            this.controllerCard = AbstractDungeon.player.discardPile.group.get(0);
        } else if ((CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed())
                && AbstractDungeon.player.discardPile
                        .size() > 5) {

            index -= 5;
            if (index < 0) {
                int wrap = AbstractDungeon.player.discardPile.size() / 5;
                index += wrap * 5;
                if (index + 5 < AbstractDungeon.player.discardPile.size()) {
                    index += 5;
                }
            }
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cY);
            this.controllerCard = AbstractDungeon.player.discardPile.group.get(index);
        } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed())
                && AbstractDungeon.player.discardPile
                        .size() > 5) {
            if (index < AbstractDungeon.player.discardPile.size() - 5) {
                index += 5;
            } else {
                index %= 5;
            }
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cY);
            this.controllerCard = AbstractDungeon.player.discardPile.group.get(index);
        } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            if (index % 5 > 0) {
                index--;
            } else {
                index += 4;
                if (index > AbstractDungeon.player.discardPile.size() - 1) {
                    index = AbstractDungeon.player.discardPile.size() - 1;
                }
            }
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cY);
            this.controllerCard = AbstractDungeon.player.discardPile.group.get(index);
        } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            if (index % 5 < 4) {
                index++;
                if (index > AbstractDungeon.player.discardPile.size() - 1) {
                    index -= AbstractDungeon.player.discardPile.size() % 5;
                }
            } else {
                index -= 4;
                if (index < 0) {
                    index = 0;
                }
            }
            Gdx.input.setCursorPosition(
                    (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cX, Settings.HEIGHT -
                            (int) ((AbstractCard) AbstractDungeon.player.discardPile.group.get(index)).hb.cY);
            this.controllerCard = AbstractDungeon.player.discardPile.group.get(index);
        }
    }

    private void updatePositions() {
        this.hoveredCard = null;
        int lineNum = 0;
        ArrayList<AbstractCard> cards = AbstractDungeon.player.discardPile.group;
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

        if (this.prevDeckSize != AbstractDungeon.player.discardPile.size()) {
            calculateScrollBounds();
        }
        resetScrolling();
        updateBarPosition();
    }

    private void calculateScrollBounds() {
        int scrollTmp = 0;
        if (AbstractDungeon.player.discardPile.size() > 10) {
            scrollTmp = AbstractDungeon.player.discardPile.size() / 5 - 2;
            if (AbstractDungeon.player.discardPile.size() % 5 != 0) {
                scrollTmp++;
            }
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT + scrollTmp * padY;
        } else {
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
        }

        this.prevDeckSize = AbstractDungeon.player.discardPile.size();
    }

    private void resetScrolling() {
        if (this.currentDiffY < this.scrollLowerBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollLowerBound);
        } else if (this.currentDiffY > this.scrollUpperBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollUpperBound);
        }
    }

    public void reopen() {
        if (Settings.isControllerMode) {
            Gdx.input.setCursorPosition(10, Settings.HEIGHT / 2);
            this.controllerCard = null;
        }
        AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
    }

    public void open() {
        if (Settings.isControllerMode) {
            Gdx.input.setCursorPosition(10, Settings.HEIGHT / 2);
            this.controllerCard = null;
        }
        CardCrawlGame.sound.play("DECK_OPEN");
        AbstractDungeon.overlayMenu.showBlackScreen();
        this.currentDiffY = this.scrollLowerBound;
        this.grabStartY = this.scrollLowerBound;
        this.grabbedScreen = false;
        AbstractDungeon.isScreenUp = true;
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.DISCARD_VIEW;
        for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
            c.setAngle(0.0F, true);
            c.targetDrawScale = 0.75F;
            c.drawScale = 0.75F;
            c.lighten(true);
        }
        hideCards();
        AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);

        if (AbstractDungeon.player.discardPile.group.size() <= 5) {
            drawStartY = Settings.HEIGHT * 0.5F;
        } else {
            drawStartY = Settings.HEIGHT * 0.66F;
        }

        calculateScrollBounds();
    }

    private void hideCards() {
        int lineNum = 0;
        ArrayList<AbstractCard> cards = AbstractDungeon.player.discardPile.group;
        for (int i = 0; i < cards.size(); i++) {
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }
            ((AbstractCard) cards.get(i)).current_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).current_y = drawStartY + this.currentDiffY - lineNum * padY
                    - MathUtils.random(100.0F * Settings.scale, 200.0F * Settings.scale);
        }
    }

    public void render(SpriteBatch sb) {
        if (shouldShowScrollBar()) {
            this.scrollBar.render(sb);
        }

        if (this.hoveredCard == null) {
            AbstractDungeon.player.discardPile.render(sb);
        } else {
            AbstractDungeon.player.discardPile.renderExceptOneCard(sb, this.hoveredCard);
            this.hoveredCard.renderHoverShadow(sb);
            this.hoveredCard.render(sb);
            this.hoveredCard.renderCardTip(sb);
        }

        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.DISCARD_PILE_BANNER, 1290.0F * Settings.xScale, 0.0F, 630.0F * Settings.scale,
                128.0F * Settings.scale);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, TEXT[2], 1558.0F * Settings.xScale,
                82.0F * Settings.scale, Settings.LIGHT_YELLOW_COLOR);

        FontHelper.renderDeckViewTip(sb, HEADER_INFO, 96.0F * Settings.scale, Settings.CREAM_COLOR);
        AbstractDungeon.overlayMenu.discardPilePanel.render(sb);
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
 * DiscardPileViewScreen.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

