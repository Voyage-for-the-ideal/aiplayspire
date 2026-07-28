package com.megacrit.cardcrawl.screens.options;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;

import java.util.ArrayList;
import java.util.Arrays;

public class DropdownMenu
        implements ScrollBarListener {
    private DropdownMenuListener listener;
    public ArrayList<DropdownRow> rows;
    public boolean isOpen = false;
    public int topVisibleRowIndex = 0;

    private int maxRows;

    private Color textColor;

    private BitmapFont textFont;

    private DropdownRow selectionBox;
    private ScrollBar scrollBar;
    private static final int DEFAULT_MAX_ROWS = 15;
    private static final float PADDING_FROM_SOURCE_HITBOX = 10.0F * Settings.scale;
    private static final float SCROLLBAR_RIGHT_PADDING = 2.0F * Settings.scale;

    private static final float BOX_EDGE_H = 32.0F * Settings.scale;
    private static final float BOX_BODY_H = 64.0F * Settings.scale;

    private boolean shouldSnapCursorToSelectedIndex = false;
    private boolean rowsHaveBeenPositioned = false;
    private float cachedMaxWidth;

    public DropdownMenu(DropdownMenuListener listener, String[] options, BitmapFont font, Color textColor) {
        this(listener, new ArrayList<>(Arrays.asList(options)), font, textColor);
    }

    public DropdownMenu(DropdownMenuListener listener, ArrayList<String> options, BitmapFont font, Color textColor) {
        this(listener, options, font, textColor, 15);
    }

    public DropdownMenu(DropdownMenuListener listener, String[] options, BitmapFont font, Color textColor,
            int maxRows) {
        this(listener, new ArrayList<>(Arrays.asList(options)), font, textColor, maxRows);
    }

    public void updateResolutionLabels(int mode) {
        this.rows.clear();
        ArrayList<String> options = CardCrawlGame.mainMenuScreen.optionPanel.getResolutionLabels(mode);
        float width = approximateOverallWidth(options) - this.scrollBar.width();

        if (options.size() > 0) {
            this.selectionBox = new DropdownRow(options.get(0), this.cachedMaxWidth, approximateRowHeight(), 0);
            this.selectionBox.isSelected = true;

            for (String option : options) {
                this.rows.add(new DropdownRow(option, width, approximateRowHeight(), this.rows.size()));
            }

            ((DropdownRow) this.rows.get(this.rows.size() - 1)).isSelected = true;
            CardCrawlGame.mainMenuScreen.optionPanel.resetResolutionDropdownSelection();
        }
    }

    boolean shouldShowSlider() {
        return (this.rows.size() > this.maxRows);
    }

    public Hitbox getHitbox() {
        return this.selectionBox.hb;
    }

    private int visibleRowCount() {
        return Math.min(this.rows.size(), this.maxRows);
    }

    void layoutRowsBelow(float originX, float originY) {
        this.selectionBox.position(originX, yPositionForRowBelow(originY, 0, 0.0F));

        for (int i = 0; i < visibleRowCount(); i++) {
            ((DropdownRow) this.rows.get(this.topVisibleRowIndex + i)).position(originX,
                    yPositionForRowBelow(originY, i + 1, 0.0F));
        }

        if (shouldShowSlider()) {
            float top = yPositionForRowBelow(originY, 1, 0.0F);
            float bottom = yPositionForRowBelow(originY, visibleRowCount() + 1, 0.0F);
            float right = originX + this.cachedMaxWidth - SCROLLBAR_RIGHT_PADDING;
            this.scrollBar.positionWithinOnRight(right, top, bottom);
        }
        this.rowsHaveBeenPositioned = true;
    }

    public float approximateRowHeight() {
        float extraSpace = Math.min(Math.max(this.textFont.getCapHeight(), 15.0F) * Settings.scale, 15.0F);
        return this.textFont.getCapHeight() + extraSpace;
    }

    private float approximateWidthForText(String option) {
        return FontHelper.getSmartWidth(this.textFont, option, Float.MAX_VALUE, Float.MAX_VALUE);
    }

    public DropdownMenu(DropdownMenuListener listener, ArrayList<String> options, BitmapFont font, Color textColor,
            int maxRows) {
        this.cachedMaxWidth = -1.0F;
        this.listener = listener;
        this.textFont = font;
        this.textColor = textColor;
        this.maxRows = maxRows;
        this.rows = new ArrayList<>(options.size());
        this.scrollBar = new ScrollBar(this);
        float width = approximateOverallWidth(options) - this.scrollBar.width();
        if (options.size() > 0) {
            this.selectionBox = new DropdownRow(options.get(0), this.cachedMaxWidth, approximateRowHeight(), 0);
            this.selectionBox.isSelected = true;
            for (String option : options)
                this.rows.add(new DropdownRow(option, width, approximateRowHeight(), this.rows.size()));
            ((DropdownRow) this.rows.get(0)).isSelected = true;
        }
    }
    private static final float ICON_WIDTH = 68.0F * Settings.scale;

    public float approximateOverallWidth() {
        if (this.cachedMaxWidth > 0.0F) {
            return this.cachedMaxWidth;
        }

        ArrayList<String> options = new ArrayList<>();
        for (DropdownRow row : this.rows) {
            options.add(row.text);
        }
        return approximateOverallWidth(options);
    }

    private float approximateOverallWidth(ArrayList<String> options) {
        if (this.cachedMaxWidth > 0.0F) {
            return this.cachedMaxWidth;
        }

        for (String option : options) {
            this.cachedMaxWidth = Math.max(this.cachedMaxWidth, approximateWidthForText(option) + ICON_WIDTH);
        }
        return this.cachedMaxWidth;
    }

    private float yPositionForRowBelow(float originY, int rowIndex, float scrollAmount) {
        float rowHeight = approximateRowHeight();
        float extraHeight = (rowIndex > 0) ? PADDING_FROM_SOURCE_HITBOX : 0.0F;
        return originY - rowHeight * rowIndex - extraHeight;
    }

    public void setSelectedIndex(int i) {
        if (this.selectionBox.index == i) {
            return;
        }

        if (i < this.rows.size()) {
            changeSelectionToRow(this.rows.get(i));
        }

        this.topVisibleRowIndex = Math.min(i, this.rows.size() - visibleRowCount());
        float scrollPercent = scrollPercentForTopVisibleRowIndex(this.topVisibleRowIndex);
        this.scrollBar.parentScrolledToPercent(scrollPercent);
    }

    public float scrollPercentForTopVisibleRowIndex(int topIndex) {
        int maxRow = this.rows.size() - visibleRowCount();
        maxRow = maxRow == 0 ? 1 : maxRow;
        return topIndex / maxRow;
    }

    public int topVisibleRowIndexForScrollPercent(float percent) {
        int maxRow = this.rows.size() - visibleRowCount();
        return (int) (maxRow * percent);
    }

    public int getSelectedIndex() {
        return this.selectionBox.index;
    }

    private void changeSelectionToRow(DropdownRow newSelection) {
        this.selectionBox.text = newSelection.text;
        this.selectionBox.index = newSelection.index;

        for (DropdownRow row : this.rows) {
            row.isSelected = (row == newSelection);
        }

        if (this.listener != null) {
            this.listener.changedSelectionTo(this, newSelection.index, newSelection.text);
        }
    }

    public void update() {
        if (this.rows.size() == 0) {
            return;
        }

        if (this.isOpen) {
            updateNonMouseInput();

            for (int i = 0; i < visibleRowCount(); i++) {
                DropdownRow row = this.rows.get(i + this.topVisibleRowIndex);
                if (row.update()) {
                    changeSelectionToRow(row);
                    this.isOpen = false;
                    CardCrawlGame.sound.play("UI_CLICK_2");
                }
            }

            if (InputHelper.scrolledDown) {
                this.topVisibleRowIndex = Integer.min(this.topVisibleRowIndex + 1,
                        this.rows.size() - visibleRowCount());
                this.scrollBar.parentScrolledToPercent(scrollPercentForTopVisibleRowIndex(this.topVisibleRowIndex));
            } else if (InputHelper.scrolledUp) {
                this.topVisibleRowIndex = Integer.max(0, this.topVisibleRowIndex - 1);
                this.scrollBar.parentScrolledToPercent(scrollPercentForTopVisibleRowIndex(this.topVisibleRowIndex));
            }

            boolean sliderClicked = false;

            if (shouldShowSlider()) {
                sliderClicked = this.scrollBar.update();
            }

            boolean shouldCloseMenu = ((InputHelper.justReleasedClickLeft && !sliderClicked)
                    || CInputActionSet.cancel.isJustPressed());

            if (shouldCloseMenu) {
                if (Settings.isControllerMode) {
                    CInputActionSet.cancel.unpress();
                    CInputHelper.setCursor(getHitbox());
                }
                this.isOpen = false;
            }

        } else if (this.selectionBox.update()) {
            this.isOpen = true;
            updateNonMouseStartPosition();
            CardCrawlGame.sound.play("UI_CLICK_1");
        }
    }

    private boolean isUsingNonMouseControl() {
        return (Settings.isControllerMode || InputActionSet.up.isJustPressed() || InputActionSet.down.isJustPressed());
    }

    private void updateNonMouseStartPosition() {
        if (!isUsingNonMouseControl()) {
            return;
        }
        this.shouldSnapCursorToSelectedIndex = true;
    }

    private void updateNonMouseInput() {
        if (!isUsingNonMouseControl()) {
            return;
        }
        if (this.shouldSnapCursorToSelectedIndex && this.rowsHaveBeenPositioned) {
            CInputHelper.setCursor(((DropdownRow) this.rows.get(this.selectionBox.index)).hb);
            this.shouldSnapCursorToSelectedIndex = false;

            return;
        }
        int hoveredIndex = -1;

        for (int i = this.topVisibleRowIndex; i < this.topVisibleRowIndex + visibleRowCount(); i++) {
            if (((DropdownRow) this.rows.get(i)).hb.hovered) {
                hoveredIndex = i;

                break;
            }
        }
        if (hoveredIndex < 0) {
            return;
        }

        boolean didInputUp = (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()
                || InputActionSet.up.isJustPressed());

        boolean didInputDown = (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()
                || InputActionSet.down.isJustPressed());
        boolean isMoving = (didInputUp || didInputDown);
        if (!isMoving) {
            return;
        }
        int targetHoverIndexOffset = didInputDown ? 1 : -1;

        int targetHoverIndex = (hoveredIndex + targetHoverIndexOffset + this.rows.size()) % this.rows.size();

        boolean isAboveTheTop = (targetHoverIndex < this.topVisibleRowIndex);
        boolean isBelowTheBottom = (targetHoverIndex >= this.topVisibleRowIndex + visibleRowCount());
        if (isAboveTheTop) {
            if (didInputDown) {
                CInputHelper.setCursor(((DropdownRow) this.rows.get(this.topVisibleRowIndex)).hb);
            }
            this.topVisibleRowIndex = targetHoverIndex;
        } else if (isBelowTheBottom) {
            if (didInputUp) {

                CInputHelper
                        .setCursor(((DropdownRow) this.rows.get(this.topVisibleRowIndex + visibleRowCount() - 1)).hb);
                ((DropdownRow) this.rows.get(targetHoverIndex)).hb.hovered = true;
            }
            this.topVisibleRowIndex = targetHoverIndex - visibleRowCount() + 1;
        } else {
            CInputHelper.setCursor(((DropdownRow) this.rows.get(targetHoverIndex)).hb);
        }

        if (shouldShowSlider()) {
            this.scrollBar.parentScrolledToPercent(scrollPercentForTopVisibleRowIndex(this.topVisibleRowIndex));
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
        if (this.rows.size() == 0) {
            return;
        }

        int rowCount = this.isOpen ? visibleRowCount() : 0;
        float heightOfSelector = approximateRowHeight() * 1.0F - BOX_EDGE_H * 2.5F;

        layoutRowsBelow(x, y);
        float topY = yPositionForRowBelow(y, 0, 0.0F);
        float bottomY = yPositionForRowBelow(y, rowCount + 1, 0.0F);
        if (this.isOpen) {
            renderBorder(sb, x, bottomY, this.cachedMaxWidth, topY - bottomY);
        }
        renderBorderFromTop(sb, x, y, this.cachedMaxWidth, heightOfSelector);

        this.selectionBox.renderRow(sb);
        if (this.isOpen) {

            for (int i = 0; i < visibleRowCount(); i++) {
                ((DropdownRow) this.rows.get(i + this.topVisibleRowIndex)).renderRow(sb);
            }
            if (shouldShowSlider()) {
                this.scrollBar.render(sb);
            }
        }

        float ARROW_ICON_W = 30.0F * Settings.scale;
        float ARROW_ICON_H = 30.0F * Settings.scale;
        float arrowIconX = x + this.cachedMaxWidth - ARROW_ICON_W - Settings.scale * 10.0F;
        float arrowIconY = y - ARROW_ICON_H;
        Texture dropdownArrowIcon = this.isOpen ? ImageMaster.OPTION_TOGGLE_ON : ImageMaster.FILTER_ARROW;
        sb.draw(dropdownArrowIcon, arrowIconX, arrowIconY, ARROW_ICON_W, ARROW_ICON_H);
    }

    private void renderBorder(SpriteBatch sb, float x, float bottom, float width, float height) {
        float border = Settings.scale * 10.0F;

        float BOX_W = width + 2.0F * border;
        float FRAME_X = x - border;

        float rowHeight = approximateRowHeight();

        sb.setColor(Color.WHITE);
        float bottomY = bottom - border;
        sb.draw(ImageMaster.KEYWORD_BOT, FRAME_X, bottomY, BOX_W, rowHeight);
        float middleHeight = height - 2.0F * rowHeight - border;
        sb.draw(ImageMaster.KEYWORD_BODY, FRAME_X, bottomY + rowHeight, BOX_W, middleHeight);
        sb.draw(ImageMaster.KEYWORD_TOP, FRAME_X, bottom + middleHeight + border, BOX_W, rowHeight);
    }

    private void renderBorderFromTop(SpriteBatch sb, float x, float top, float width, float height) {
        float border = Settings.scale * 10.0F;

        float BORDER_TOP_Y = top - BOX_EDGE_H + border;
        float BOX_W = width + 2.0F * border;
        float FRAME_X = x - border;

        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.KEYWORD_TOP, FRAME_X, BORDER_TOP_Y, BOX_W, BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BODY, FRAME_X, BORDER_TOP_Y - height - BOX_EDGE_H, BOX_W, height + BOX_EDGE_H);
        sb.draw(ImageMaster.KEYWORD_BOT, FRAME_X, BORDER_TOP_Y - height - BOX_BODY_H, BOX_W, BOX_EDGE_H);
    }

    public void scrolledUsingBar(float newPercent) {
        this.topVisibleRowIndex = topVisibleRowIndexForScrollPercent(newPercent);
        this.scrollBar.parentScrolledToPercent(newPercent);
    }

    private class DropdownRow {
        String text;
        Hitbox hb;
        boolean isSelected = false;
        int index;

        DropdownRow(String text, float width, float height, int index) {
            this.text = text;
            this.hb = new Hitbox(width, height);
            this.index = index;
        }

        private void position(float x, float y) {
            this.hb.x = x;
            this.hb.y = y - this.hb.height;
            this.hb.cX = this.hb.x + this.hb.width / 2.0F;
            this.hb.cY = this.hb.y + this.hb.height / 2.0F;
        }

        private boolean update() {
            this.hb.update();

            if (this.hb.hovered && InputHelper.justClickedLeft) {
                this.hb.clickStarted = true;
            }

            if (this.hb.clicked || (this.hb.hovered && CInputActionSet.select.isJustPressed())) {
                this.hb.clicked = false;
                return true;
            }
            return false;
        }

        private void renderRow(SpriteBatch sb) {
            this.hb.render(sb);

            Color renderTextColor = DropdownMenu.this.textColor;
            if (this.hb.hovered) {
                renderTextColor = Settings.GREEN_TEXT_COLOR;
            } else if (this.isSelected) {
                renderTextColor = Settings.GOLD_COLOR;
            }

            float textX = this.hb.x + Settings.scale * 10.0F;
            float a = DropdownMenu.this.approximateRowHeight();
            float b = DropdownMenu.this.textFont.getCapHeight();
            float yOffset = (a - b) / 2.0F;
            float textY = this.hb.y - yOffset;

            FontHelper.renderSmartText(sb, DropdownMenu.this

                    .textFont, this.text, textX, textY + this.hb.height, Float.MAX_VALUE, Float.MAX_VALUE,
                    renderTextColor);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\options\
 * DropdownMenu.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

