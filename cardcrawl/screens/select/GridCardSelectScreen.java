package com.megacrit.cardcrawl.screens.select;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.ui.buttons.GridSelectConfirmButton;
import com.megacrit.cardcrawl.ui.buttons.PeekButton;
import com.megacrit.cardcrawl.vfx.FastCardObtainEffect;

import java.util.ArrayList;

public class GridCardSelectScreen
        implements ScrollBarListener {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("GridCardSelectScreen");
    public static final String[] TEXT = uiStrings.TEXT;
    private static float drawStartX;
    private static float drawStartY;
    private static float padX;
    private static float padY;
    private static final int CARDS_PER_LINE = 5;
    private static final float SCROLL_BAR_THRESHOLD = 500.0F * Settings.scale;
    private float grabStartY = 0.0F;
    private float currentDiffY = 0.0F;
    public ArrayList<AbstractCard> selectedCards = new ArrayList<>();
    public CardGroup targetGroup;
    private AbstractCard hoveredCard = null;
    public AbstractCard upgradePreviewCard = null;
    private int numCards = 0;
    private int cardSelectAmount = 0;
    private float scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    private float scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
    private boolean grabbedScreen = false;
    private boolean canCancel = true;
    public boolean forUpgrade = false;
    public GridSelectConfirmButton confirmButton = new GridSelectConfirmButton(TEXT[0]);
    public boolean forTransform = false;
    public boolean forPurge = false;
    public boolean confirmScreenUp = false;
    public boolean isJustForConfirming = false;
    public PeekButton peekButton = new PeekButton();
    private String tipMsg = "";
    private String lastTip = "";
    private float ritualAnimTimer = 0.0F;
    private static final float RITUAL_ANIM_INTERVAL = 0.1F;
    private int prevDeckSize = 0;
    public boolean cancelWasOn = false;
    public boolean anyNumber = false;
    public boolean forClarity = false;
    public String cancelText;
    private ScrollBar scrollBar;
    private AbstractCard controllerCard = null;

    private static final int ARROW_W = 64;

    private float arrowScale1 = 1.0F, arrowScale2 = 1.0F, arrowScale3 = 1.0F, arrowTimer = 0.0F;

    public GridCardSelectScreen() {
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
        updateControllerInput();
        updatePeekButton();

        if (PeekButton.isPeeking) {
            return;
        }

        if (Settings.isControllerMode && this.controllerCard != null && !CardCrawlGame.isPopupOpen
                && this.upgradePreviewCard == null) {
            if (Gdx.input.getY() > Settings.HEIGHT * 0.75F) {
                this.currentDiffY += Settings.SCROLL_SPEED;
            } else if (Gdx.input.getY() < Settings.HEIGHT * 0.25F) {
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

        if (this.forClarity) {
            if (this.selectedCards.size() > 0) {
                this.confirmButton.isDisabled = false;
            } else {
                this.confirmButton.isDisabled = true;
            }
        }

        this.confirmButton.update();

        if (this.isJustForConfirming) {
            updateCardPositionsAndHoverLogic();
            if (this.confirmButton.hb.clicked || CInputActionSet.topPanel.isJustPressed()) {
                CInputActionSet.select.unpress();
                this.confirmButton.hb.clicked = false;
                AbstractDungeon.overlayMenu.cancelButton.hide();
                AbstractDungeon.dynamicBanner.hide();
                this.confirmScreenUp = false;
                for (AbstractCard c : this.targetGroup.group) {
                    AbstractDungeon.topLevelEffects.add(new FastCardObtainEffect(c, c.current_x, c.current_y));
                }
                AbstractDungeon.closeCurrentScreen();
            }
            return;
        }
        if ((this.anyNumber || this.forClarity) &&
                this.confirmButton.hb.clicked) {
            this.confirmButton.hb.clicked = false;
            AbstractDungeon.closeCurrentScreen();

            return;
        }

        if (!this.confirmScreenUp) {
            updateCardPositionsAndHoverLogic();

            if (this.hoveredCard != null && InputHelper.justClickedLeft) {
                this.hoveredCard.hb.clickStarted = true;
            }

            if (this.hoveredCard != null && (this.hoveredCard.hb.clicked || CInputActionSet.select.isJustPressed())) {
                this.hoveredCard.hb.clicked = false;
                if (!this.selectedCards.contains(this.hoveredCard)) {
                    if (this.forClarity && this.selectedCards.size() > 0) {
                        ((AbstractCard) this.selectedCards.get(0)).stopGlowing();
                        this.selectedCards.clear();
                        this.cardSelectAmount--;
                    }
                    this.selectedCards.add(this.hoveredCard);
                    this.hoveredCard.beginGlowing();
                    this.hoveredCard.targetDrawScale = 0.75F;
                    this.hoveredCard.drawScale = 0.875F;

                    this.cardSelectAmount++;
                    CardCrawlGame.sound.play("CARD_SELECT");

                    if (this.numCards == this.cardSelectAmount) {
                        if (this.forUpgrade) {

                            this.hoveredCard.untip();
                            this.confirmScreenUp = true;
                            this.upgradePreviewCard = this.hoveredCard.makeStatEquivalentCopy();
                            this.upgradePreviewCard.upgrade();
                            this.upgradePreviewCard.displayUpgrades();
                            this.upgradePreviewCard.drawScale = 0.875F;
                            this.hoveredCard.stopGlowing();
                            this.selectedCards.clear();
                            AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
                            this.confirmButton.show();
                            this.confirmButton.isDisabled = false;
                            this.lastTip = this.tipMsg;
                            this.tipMsg = TEXT[2];
                            return;
                        }
                        if (this.forTransform) {

                            this.hoveredCard.untip();
                            this.confirmScreenUp = true;
                            this.upgradePreviewCard = this.hoveredCard.makeStatEquivalentCopy();
                            this.upgradePreviewCard.drawScale = 0.875F;
                            this.hoveredCard.stopGlowing();
                            this.selectedCards.clear();
                            AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
                            this.confirmButton.show();
                            this.confirmButton.isDisabled = false;
                            this.lastTip = this.tipMsg;
                            this.tipMsg = TEXT[2];
                            return;
                        }
                        if (this.forPurge) {
                            if (this.numCards == 1) {
                                this.hoveredCard.untip();
                                this.hoveredCard.stopGlowing();
                                this.confirmScreenUp = true;
                                this.hoveredCard.current_x = Settings.WIDTH / 2.0F;
                                this.hoveredCard.target_x = Settings.WIDTH / 2.0F;
                                this.hoveredCard.current_y = Settings.HEIGHT / 2.0F;
                                this.hoveredCard.target_y = Settings.HEIGHT / 2.0F;
                                this.hoveredCard.update();
                                this.hoveredCard.targetDrawScale = 1.0F;
                                this.hoveredCard.drawScale = 1.0F;
                                this.selectedCards.clear();
                                this.confirmButton.show();
                                this.confirmButton.isDisabled = false;
                                this.lastTip = this.tipMsg;
                                this.tipMsg = TEXT[2];
                                AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
                            } else {
                                AbstractDungeon.closeCurrentScreen();
                            }
                            for (AbstractCard c : this.selectedCards)
                                c.stopGlowing();
                            return;
                        }
                        if (!this.anyNumber) {

                            AbstractDungeon.closeCurrentScreen();
                            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.SHOP) {
                                AbstractDungeon.overlayMenu.cancelButton.hide();
                            } else {
                                AbstractDungeon.overlayMenu.cancelButton.show(TEXT[3]);
                            }

                            for (AbstractCard c : this.selectedCards) {
                                c.stopGlowing();
                            }

                            if (this.targetGroup.type == CardGroup.CardGroupType.DISCARD_PILE)
                                for (AbstractCard c : this.targetGroup.group) {
                                    c.drawScale = 0.12F;
                                    c.targetDrawScale = 0.12F;
                                    c.teleportToDiscardPile();
                                    c.lighten(true);
                                }
                            return;
                        }
                        if (this.cardSelectAmount < this.targetGroup.size() && this.anyNumber) {
                            AbstractDungeon.closeCurrentScreen();
                            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.SHOP) {
                                AbstractDungeon.overlayMenu.cancelButton.hide();
                            } else {
                                AbstractDungeon.overlayMenu.cancelButton.show(TEXT[3]);
                            }

                            for (AbstractCard c : this.selectedCards) {
                                c.stopGlowing();
                            }

                            if (this.targetGroup.type == CardGroup.CardGroupType.DISCARD_PILE) {
                                for (AbstractCard c : this.targetGroup.group) {
                                    c.drawScale = 0.12F;
                                    c.targetDrawScale = 0.12F;
                                    c.teleportToDiscardPile();
                                    c.lighten(true);
                                }
                            }
                        }
                    }
                } else if (this.selectedCards.contains(this.hoveredCard)) {
                    this.hoveredCard.stopGlowing();
                    this.selectedCards.remove(this.hoveredCard);
                    this.cardSelectAmount--;
                }

                return;
            }
        } else {
            if (this.forTransform) {
                this.ritualAnimTimer -= Gdx.graphics.getDeltaTime();
                if (this.ritualAnimTimer < 0.0F) {
                    this.upgradePreviewCard = AbstractDungeon
                            .returnTrulyRandomCardFromAvailable(this.upgradePreviewCard).makeCopy();
                    this.ritualAnimTimer = 0.1F;
                }
            }

            if (this.forUpgrade) {
                this.upgradePreviewCard.update();
            }
            if (!this.forPurge) {
                this.upgradePreviewCard.drawScale = 1.0F;
                this.hoveredCard.update();
                this.hoveredCard.drawScale = 1.0F;
            }

            if (this.confirmButton.hb.clicked || CInputActionSet.topPanel.isJustPressed()) {
                CInputActionSet.select.unpress();
                this.confirmButton.hb.clicked = false;
                AbstractDungeon.overlayMenu.cancelButton.hide();
                this.confirmScreenUp = false;
                this.selectedCards.add(this.hoveredCard);
                AbstractDungeon.closeCurrentScreen();
            }
        }

        if (Settings.isControllerMode) {
            if (this.upgradePreviewCard != null) {
                CInputHelper.setCursor(this.upgradePreviewCard.hb);
            } else if (this.controllerCard != null) {
                CInputHelper.setCursor(this.controllerCard.hb);
            }
        }
    }

    private void updatePeekButton() {
        this.peekButton.update();
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode || this.upgradePreviewCard != null) {
            return;
        }

        boolean anyHovered = false;
        int index = 0;

        for (AbstractCard c : this.targetGroup.group) {
            if (c.hb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered && this.controllerCard == null) {
            CInputHelper.setCursor(((AbstractCard) this.targetGroup.group.get(0)).hb);
            this.controllerCard = this.targetGroup.group.get(0);
        } else if ((CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) && this.targetGroup
                .size() > 5) {

            if (index < 5) {
                index = this.targetGroup.size() + 2 - 4 - index;
                if (index > this.targetGroup.size() - 1) {
                    index -= 5;
                }
                if (index > this.targetGroup.size() - 1 || index < 0) {
                    index = 0;
                }
            } else {

                index -= 5;
            }
            CInputHelper.setCursor(((AbstractCard) this.targetGroup.group.get(index)).hb);
            this.controllerCard = this.targetGroup.group.get(index);
        } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) && this.targetGroup
                .size() > 5) {
            if (index < this.targetGroup.size() - 5) {
                index += 5;
            } else {
                index %= 5;
            }
            CInputHelper.setCursor(((AbstractCard) this.targetGroup.group.get(index)).hb);
            this.controllerCard = this.targetGroup.group.get(index);
        } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            if (index % 5 > 0) {
                index--;
            } else {
                index += 4;
                if (index > this.targetGroup.size() - 1) {
                    index = this.targetGroup.size() - 1;
                }
            }
            CInputHelper.setCursor(((AbstractCard) this.targetGroup.group.get(index)).hb);
            this.controllerCard = this.targetGroup.group.get(index);
        } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            if (index % 5 < 4) {
                index++;
                if (index > this.targetGroup.size() - 1) {
                    index -= this.targetGroup.size() % 5;
                }
            } else {
                index -= 4;
                if (index < 0) {
                    index = 0;
                }
            }

            if (index > this.targetGroup.group.size() - 1) {
                index = 0;
            }
            CInputHelper.setCursor(((AbstractCard) this.targetGroup.group.get(index)).hb);
            this.controllerCard = this.targetGroup.group.get(index);
        }
    }

    private void updateCardPositionsAndHoverLogic() {
        if (this.isJustForConfirming && this.targetGroup.size() <= 4) {
            switch (this.targetGroup.size()) {
                case 1:
                    (this.targetGroup.getBottomCard()).current_x = Settings.WIDTH / 2.0F;
                    (this.targetGroup.getBottomCard()).target_x = Settings.WIDTH / 2.0F;
                    break;
                case 2:
                    ((AbstractCard) this.targetGroup.group.get(0)).current_x = Settings.WIDTH / 2.0F - padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(0)).target_x = Settings.WIDTH / 2.0F - padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(1)).current_x = Settings.WIDTH / 2.0F + padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(1)).target_x = Settings.WIDTH / 2.0F + padX / 2.0F;
                    break;
                case 3:
                    ((AbstractCard) this.targetGroup.group.get(0)).current_x = drawStartX + padX;
                    ((AbstractCard) this.targetGroup.group.get(1)).current_x = drawStartX + padX * 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(2)).current_x = drawStartX + padX * 3.0F;
                    ((AbstractCard) this.targetGroup.group.get(0)).target_x = drawStartX + padX;
                    ((AbstractCard) this.targetGroup.group.get(1)).target_x = drawStartX + padX * 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(2)).target_x = drawStartX + padX * 3.0F;
                    break;
                case 4:
                    ((AbstractCard) this.targetGroup.group.get(0)).current_x = Settings.WIDTH / 2.0F - padX / 2.0F
                            - padX;
                    ((AbstractCard) this.targetGroup.group.get(0)).target_x = Settings.WIDTH / 2.0F - padX / 2.0F
                            - padX;
                    ((AbstractCard) this.targetGroup.group.get(1)).current_x = Settings.WIDTH / 2.0F - padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(1)).target_x = Settings.WIDTH / 2.0F - padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(2)).current_x = Settings.WIDTH / 2.0F + padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(2)).target_x = Settings.WIDTH / 2.0F + padX / 2.0F;
                    ((AbstractCard) this.targetGroup.group.get(3)).current_x = Settings.WIDTH / 2.0F + padX / 2.0F
                            + padX;
                    ((AbstractCard) this.targetGroup.group.get(3)).target_x = Settings.WIDTH / 2.0F + padX / 2.0F
                            + padX;
                    break;
            }

            ArrayList<AbstractCard> c2 = this.targetGroup.group;

            for (int j = 0; j < c2.size(); j++) {
                ((AbstractCard) c2.get(j)).target_y = drawStartY + this.currentDiffY;
                ((AbstractCard) c2.get(j)).fadingOut = false;
                ((AbstractCard) c2.get(j)).update();
                ((AbstractCard) c2.get(j)).updateHoverLogic();

                this.hoveredCard = null;
                for (AbstractCard c : c2) {
                    if (c.hb.hovered) {
                        this.hoveredCard = c;
                    }
                }
            }

            return;
        }

        int lineNum = 0;
        ArrayList<AbstractCard> cards = this.targetGroup.group;
        for (int i = 0; i < cards.size(); i++) {
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }
            ((AbstractCard) cards.get(i)).target_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).target_y = drawStartY + this.currentDiffY - lineNum * padY;
            ((AbstractCard) cards.get(i)).fadingOut = false;
            ((AbstractCard) cards.get(i)).update();
            ((AbstractCard) cards.get(i)).updateHoverLogic();

            this.hoveredCard = null;
            for (AbstractCard c : cards) {
                if (c.hb.hovered) {
                    this.hoveredCard = c;
                }
            }
        }
    }

    public void open(CardGroup group, int numCards, boolean anyNumber, String msg) {
        open(group, numCards, msg, false, false, true, false);

        this.anyNumber = anyNumber;
        this.forClarity = !anyNumber;
        this.confirmButton.hideInstantly();
        this.confirmButton.show();
        this.confirmButton.updateText(TEXT[0]);
        this.confirmButton.isDisabled = !anyNumber;
    }

    public void open(CardGroup group, int numCards, String tipMsg, boolean forUpgrade, boolean forTransform,
            boolean canCancel, boolean forPurge) {
        this.targetGroup = group;
        callOnOpen();

        this.forUpgrade = forUpgrade;
        this.forTransform = forTransform;
        this.canCancel = canCancel;
        this.forPurge = forPurge;
        this.tipMsg = tipMsg;
        this.numCards = numCards;

        if ((forUpgrade || forTransform || forPurge
                || AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.SHOP) && canCancel) {
            AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
        }

        if (!canCancel) {
            AbstractDungeon.overlayMenu.cancelButton.hide();
        }

        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            this.peekButton.hideInstantly();
            this.peekButton.show();
        }

        calculateScrollBounds();
    }

    public void open(CardGroup group, int numCards, String tipMsg, boolean forUpgrade, boolean forRitual) {
        open(group, numCards, tipMsg, forUpgrade, forRitual, true, false);
    }

    public void open(CardGroup group, int numCards, String tipMsg, boolean forUpgrade) {
        open(group, numCards, tipMsg, forUpgrade, false);
    }

    public void openConfirmationGrid(CardGroup group, String tipMsg) {
        this.targetGroup = group;
        callOnOpen();

        this.isJustForConfirming = true;
        this.tipMsg = tipMsg;

        AbstractDungeon.overlayMenu.cancelButton.hideInstantly();

        this.confirmButton.show();
        this.confirmButton.updateText(TEXT[0]);
        this.confirmButton.isDisabled = false;

        this.canCancel = false;

        if (group.size() <= 5) {
            AbstractDungeon.dynamicBanner.appear(tipMsg);
        }
    }

    private void callOnOpen() {
        if (Settings.isControllerMode) {
            Gdx.input.setCursorPosition(10, Settings.HEIGHT / 2);
            this.controllerCard = null;
        }

        this.anyNumber = false;
        this.forClarity = false;
        this.canCancel = false;
        this.forUpgrade = false;
        this.forTransform = false;
        this.forPurge = false;
        this.confirmScreenUp = false;
        this.isJustForConfirming = false;
        AbstractDungeon.overlayMenu.proceedButton.hide();
        this.controllerCard = null;
        this.hoveredCard = null;
        this.selectedCards.clear();
        AbstractDungeon.topPanel.unhoverHitboxes();
        this.cardSelectAmount = 0;
        this.currentDiffY = 0.0F;
        this.grabStartY = 0.0F;
        this.grabbedScreen = false;
        hideCards();
        AbstractDungeon.isScreenUp = true;
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.GRID;
        AbstractDungeon.overlayMenu.showBlackScreen(0.75F);
        this.confirmButton.hideInstantly();
        this.peekButton.hideInstantly();
        if (this.targetGroup.group.size() <= 5) {
            drawStartY = Settings.HEIGHT * 0.5F;
        } else {
            drawStartY = Settings.HEIGHT * 0.66F;
        }
    }

    public void reopen() {
        AbstractDungeon.overlayMenu.showBlackScreen(0.75F);
        AbstractDungeon.isScreenUp = true;
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.GRID;
        AbstractDungeon.topPanel.unhoverHitboxes();
        if (this.cancelWasOn && !this.isJustForConfirming && this.canCancel) {
            AbstractDungeon.overlayMenu.cancelButton.show(this.cancelText);
        }
        for (AbstractCard c : this.targetGroup.group) {
            c.targetDrawScale = 0.75F;
            c.drawScale = 0.75F;
            c.lighten(false);
        }
        this.scrollBar.reset();
    }

    public void hide() {
        if (!AbstractDungeon.overlayMenu.cancelButton.isHidden) {
            this.cancelWasOn = true;
            this.cancelText = AbstractDungeon.overlayMenu.cancelButton.buttonText;
        }
    }

    private void updateScrolling() {
        if (PeekButton.isPeeking) {
            return;
        }

        if (this.isJustForConfirming && this.targetGroup.size() <= 5) {
            this.currentDiffY = -64.0F * Settings.scale;

            return;
        }
        int y = InputHelper.mY;
        boolean isDraggingScrollBar = this.scrollBar.update();

        if (!isDraggingScrollBar) {
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
        }

        if (this.prevDeckSize != this.targetGroup.size()) {
            calculateScrollBounds();
        }
        resetScrolling();
        updateBarPosition();
    }

    private void calculateScrollBounds() {
        int scrollTmp = 0;
        if (this.targetGroup.size() > 10) {
            scrollTmp = this.targetGroup.size() / 5 - 2;
            if (this.targetGroup.size() % 5 != 0) {
                scrollTmp++;
            }
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT + scrollTmp * padY;
        } else {
            this.scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
        }

        this.prevDeckSize = this.targetGroup.size();
    }

    private void resetScrolling() {
        if (this.currentDiffY < this.scrollLowerBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollLowerBound);
        } else if (this.currentDiffY > this.scrollUpperBound) {
            this.currentDiffY = MathHelper.scrollSnapLerpSpeed(this.currentDiffY, this.scrollUpperBound);
        }
    }

    private void hideCards() {
        int lineNum = 0;
        ArrayList<AbstractCard> cards = this.targetGroup.group;
        for (int i = 0; i < cards.size(); i++) {
            ((AbstractCard) cards.get(i)).setAngle(0.0F, true);
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }

            ((AbstractCard) cards.get(i)).lighten(true);
            ((AbstractCard) cards.get(i)).current_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).current_y = drawStartY + this.currentDiffY - lineNum * padY
                    - MathUtils.random(100.0F * Settings.scale, 200.0F * Settings.scale);

            ((AbstractCard) cards.get(i)).targetDrawScale = 0.75F;
            ((AbstractCard) cards.get(i)).drawScale = 0.75F;
        }
    }

    public void cancelUpgrade() {
        this.cardSelectAmount = 0;
        this.confirmScreenUp = false;
        this.confirmButton.hide();
        this.confirmButton.isDisabled = true;
        this.hoveredCard = null;
        this.upgradePreviewCard = null;

        if (Settings.isControllerMode && this.controllerCard != null) {
            this.hoveredCard = this.controllerCard;
            CInputHelper.setCursor(this.hoveredCard.hb);
        }

        if ((this.forUpgrade || this.forTransform || this.forPurge
                || AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.SHOP) && this.canCancel) {
            AbstractDungeon.overlayMenu.cancelButton.show(TEXT[1]);
        }

        int lineNum = 0;
        ArrayList<AbstractCard> cards = this.targetGroup.group;
        for (int i = 0; i < cards.size(); i++) {
            int mod = i % 5;
            if (mod == 0 && i != 0) {
                lineNum++;
            }
            ((AbstractCard) cards.get(i)).current_x = drawStartX + mod * padX;
            ((AbstractCard) cards.get(i)).current_y = drawStartY + this.currentDiffY - lineNum * padY;
        }

        this.tipMsg = this.lastTip;
    }

    public void render(SpriteBatch sb) {
        if (shouldShowScrollBar()) {
            this.scrollBar.render(sb);
        }

        if (!PeekButton.isPeeking) {
            if (this.hoveredCard != null) {
                if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
                    this.targetGroup.renderExceptOneCard(sb, this.hoveredCard);
                } else {
                    this.targetGroup.renderExceptOneCardShowBottled(sb, this.hoveredCard);
                }

                this.hoveredCard.renderHoverShadow(sb);
                this.hoveredCard.render(sb);

                if ((AbstractDungeon.getCurrRoom()).phase != AbstractRoom.RoomPhase.COMBAT) {
                    if (this.hoveredCard.inBottleFlame) {
                        AbstractRelic tmp = RelicLibrary.getRelic("Bottled Flame");
                        float prevX = tmp.currentX;
                        float prevY = tmp.currentY;
                        tmp.currentX = this.hoveredCard.current_x + 130.0F * Settings.scale;
                        tmp.currentY = this.hoveredCard.current_y + 182.0F * Settings.scale;
                        tmp.scale = this.hoveredCard.drawScale * Settings.scale * 1.5F;
                        tmp.render(sb);
                        tmp.currentX = prevX;
                        tmp.currentY = prevY;
                        tmp = null;
                    } else if (this.hoveredCard.inBottleLightning) {
                        AbstractRelic tmp = RelicLibrary.getRelic("Bottled Lightning");
                        float prevX = tmp.currentX;
                        float prevY = tmp.currentY;
                        tmp.currentX = this.hoveredCard.current_x + 130.0F * Settings.scale;
                        tmp.currentY = this.hoveredCard.current_y + 182.0F * Settings.scale;
                        tmp.scale = this.hoveredCard.drawScale * Settings.scale * 1.5F;
                        tmp.render(sb);
                        tmp.currentX = prevX;
                        tmp.currentY = prevY;
                        tmp = null;
                    } else if (this.hoveredCard.inBottleTornado) {
                        AbstractRelic tmp = RelicLibrary.getRelic("Bottled Tornado");
                        float prevX = tmp.currentX;
                        float prevY = tmp.currentY;
                        tmp.currentX = this.hoveredCard.current_x + 130.0F * Settings.scale;
                        tmp.currentY = this.hoveredCard.current_y + 182.0F * Settings.scale;
                        tmp.scale = this.hoveredCard.drawScale * Settings.scale * 1.5F;
                        tmp.render(sb);
                        tmp.currentX = prevX;
                        tmp.currentY = prevY;
                        tmp = null;
                    }
                }
                this.hoveredCard.renderCardTip(sb);
            } else if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
                this.targetGroup.render(sb);
            } else {
                this.targetGroup.renderShowBottled(sb);
            }
        }

        if (this.confirmScreenUp) {
            sb.setColor(new Color(0.0F, 0.0F, 0.0F, 0.8F));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT - 64.0F * Settings.scale);

            if (this.forTransform || this.forUpgrade) {
                renderArrows(sb);

                this.hoveredCard.current_x = Settings.WIDTH * 0.36F;
                this.hoveredCard.current_y = Settings.HEIGHT / 2.0F;
                this.hoveredCard.target_x = Settings.WIDTH * 0.36F;
                this.hoveredCard.target_y = Settings.HEIGHT / 2.0F;
                this.hoveredCard.render(sb);
                this.hoveredCard.updateHoverLogic();
                this.hoveredCard.renderCardTip(sb);

                this.upgradePreviewCard.current_x = Settings.WIDTH * 0.63F;
                this.upgradePreviewCard.current_y = Settings.HEIGHT / 2.0F;
                this.upgradePreviewCard.target_x = Settings.WIDTH * 0.63F;
                this.upgradePreviewCard.target_y = Settings.HEIGHT / 2.0F;
                this.upgradePreviewCard.render(sb);
                this.upgradePreviewCard.updateHoverLogic();
                this.upgradePreviewCard.renderCardTip(sb);
            } else {

                this.hoveredCard.current_x = Settings.WIDTH / 2.0F;
                this.hoveredCard.current_y = Settings.HEIGHT / 2.0F;
                this.hoveredCard.render(sb);
                this.hoveredCard.updateHoverLogic();
            }
        }

        if (!PeekButton.isPeeking && (this.forUpgrade || this.forTransform || this.forPurge || this.isJustForConfirming
                || this.anyNumber || this.forClarity)) {
            this.confirmButton.render(sb);
        }

        this.peekButton.render(sb);

        if ((!this.isJustForConfirming || this.targetGroup.size() > 5) && !PeekButton.isPeeking) {
            FontHelper.renderDeckViewTip(sb, this.tipMsg, 96.0F * Settings.scale, Settings.CREAM_COLOR);
        }
    }

    private void renderArrows(SpriteBatch sb) {
        float x = Settings.WIDTH / 2.0F - 73.0F * Settings.scale - 32.0F;
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.UPGRADE_ARROW, x, Settings.HEIGHT / 2.0F - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                this.arrowScale1 * Settings.scale, this.arrowScale1 * Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

        x += 64.0F * Settings.scale;
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.UPGRADE_ARROW, x, Settings.HEIGHT / 2.0F - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                this.arrowScale2 * Settings.scale, this.arrowScale2 * Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

        x += 64.0F * Settings.scale;

        sb.draw(ImageMaster.UPGRADE_ARROW, x, Settings.HEIGHT / 2.0F - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                this.arrowScale3 * Settings.scale, this.arrowScale3 * Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

        this.arrowTimer += Gdx.graphics.getDeltaTime() * 2.0F;
        this.arrowScale1 = 0.8F + (MathUtils.cos(this.arrowTimer) + 1.0F) / 8.0F;
        this.arrowScale2 = 0.8F + (MathUtils.cos(this.arrowTimer - 0.8F) + 1.0F) / 8.0F;
        this.arrowScale3 = 0.8F + (MathUtils.cos(this.arrowTimer - 1.6F) + 1.0F) / 8.0F;
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
        return (!this.confirmScreenUp && this.scrollUpperBound > SCROLL_BAR_THRESHOLD && !PeekButton.isPeeking);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\select\
 * GridCardSelectScreen.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

