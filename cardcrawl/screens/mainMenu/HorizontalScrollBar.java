package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class HorizontalScrollBar {
    public ScrollBarListener sliderListener;
    public boolean isBackgroundVisible = true;
    private Hitbox sliderHb;
    private float currentScrollPercent;
    private boolean isDragging;
    public static final float TRACK_H = 54.0F * Settings.scale;
    private final float TRACK_CAP_WIDTH = TRACK_H;
    private final float CURSOR_W = 60.0F * Settings.scale;
    private final float CURSOR_H = 38.0F * Settings.scale;
    private final float DRAW_BORDER = this.CURSOR_W / 4.0F;

    private float cursorDrawPosition = 0.0F;

    public HorizontalScrollBar(ScrollBarListener listener) {
        this(listener, Settings.WIDTH / 2.0F, Settings.HEIGHT - 150.0F * Settings.scale - TRACK_H / 2.0F,
                Settings.WIDTH - 256.0F * Settings.scale);

        this.cursorDrawPosition = getXPositionForPercent(0.0F);
    }

    public HorizontalScrollBar(ScrollBarListener listener, float x, float y, float width) {
        this.sliderListener = listener;
        this.currentScrollPercent = 0.0F;
        this.isDragging = false;

        this.sliderHb = new Hitbox(width, TRACK_H);
        this.sliderHb.move(x, y);
        reset();
    }

    public void setCenter(float x, float y) {
        this.sliderHb.move(x, y);
        reset();
    }

    public void move(float xOffset, float yOffset) {
        this.sliderHb.move(this.sliderHb.cX + xOffset, this.sliderHb.cY + yOffset);
        reset();
    }

    public void changeWidth(float newWidth) {
        this.sliderHb.width = newWidth;
        this.sliderHb.move(this.sliderHb.cX, this.sliderHb.cY);
        reset();
    }

    public float width() {
        return this.sliderHb.width;
    }

    public void reset() {
        this.cursorDrawPosition = getXPositionForPercent(0.0F);
    }

    public boolean update() {
        this.sliderHb.update();
        if (this.sliderHb.hovered && InputHelper.isMouseDown) {
            this.isDragging = true;
        }
        if (this.isDragging && InputHelper.justReleasedClickLeft) {
            this.isDragging = false;
            return true;
        }
        if (this.isDragging) {
            float newPercent = getPercentFromX(InputHelper.mX);
            this.sliderListener.scrolledUsingBar(newPercent);
            return true;
        }

        return false;
    }

    public void parentScrolledToPercent(float percent) {
        this.currentScrollPercent = boundedPercent(percent);
    }

    private float getPercentFromX(float x) {
        float minX = this.sliderHb.x + this.DRAW_BORDER;
        float maxX = this.sliderHb.x + this.sliderHb.width - this.DRAW_BORDER;
        return boundedPercent(MathHelper.percentFromValueBetween(minX, maxX, x));
    }

    public void render(SpriteBatch sb) {
        Color previousColor = sb.getColor();
        sb.setColor(Color.WHITE);

        if (this.isBackgroundVisible) {
            renderTrack(sb);
        }
        renderCursor(sb);
        this.sliderHb.render(sb);
        sb.setColor(previousColor);
    }

    private float getXPositionForPercent(float percent) {
        float topX = this.sliderHb.x - this.DRAW_BORDER;
        float bottomX = this.sliderHb.x + this.sliderHb.width - this.CURSOR_W + this.DRAW_BORDER;
        return MathHelper.valueFromPercentBetween(topX, bottomX, boundedPercent(percent));
    }

    private void renderCursor(SpriteBatch sb) {
        float y = this.sliderHb.cY - this.CURSOR_H / 2.0F;
        float xForPercent = getXPositionForPercent(this.currentScrollPercent);

        this.cursorDrawPosition = MathHelper.scrollSnapLerpSpeed(this.cursorDrawPosition, xForPercent);
        sb.draw(ImageMaster.SCROLL_BAR_HORIZONTAL_TRAIN, this.cursorDrawPosition, y, this.CURSOR_W, this.CURSOR_H);
    }

    private void renderTrack(SpriteBatch sb) {
        sb.draw(ImageMaster.SCROLL_BAR_HORIZONTAL_MIDDLE, this.sliderHb.x, this.sliderHb.y, this.sliderHb.width,
                this.sliderHb.height);
        sb.draw(ImageMaster.SCROLL_BAR_RIGHT, this.sliderHb.x + this.sliderHb.width, this.sliderHb.y,
                this.TRACK_CAP_WIDTH, this.sliderHb.height);

        sb.draw(ImageMaster.SCROLL_BAR_LEFT, this.sliderHb.x - this.TRACK_CAP_WIDTH, this.sliderHb.y,
                this.TRACK_CAP_WIDTH, this.sliderHb.height);
    }

    private float boundedPercent(float percent) {
        return Math.max(0.0F, Math.min(percent, 1.0F));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\mainMenu\
 * HorizontalScrollBar.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

