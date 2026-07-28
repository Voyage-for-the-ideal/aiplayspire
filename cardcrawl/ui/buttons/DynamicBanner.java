package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.TintEffect;

public class DynamicBanner {
    public static final int RAW_W = 1112;
    public static final int RAW_H = 238;
    private static final float Y_OFFSET = -50.0F * Settings.scale;
    private static final float ANIM_TIME = 0.5F;
    private static final float LERP_SPEED = 9.0F;
    private static final Color TEXT_SHOW_COLOR = new Color(0.9F, 0.9F, 0.9F, 1.0F);
    private static final Color IDLE_COLOR = new Color(0.7F, 0.7F, 0.7F, 1.0F);
    private static final Color FADE_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.0F);

    private String label;
    private float animateTimer = 0.0F;
    public float y;
    public float targetY;
    public static final float Y = Settings.HEIGHT / 2.0F + 260.0F * Settings.scale;
    public float startY;
    public float scale;
    protected TintEffect tint = new TintEffect();
    protected TintEffect textTint = new TintEffect();
    public boolean pressed = false;
    public boolean isMoving = false;
    public boolean show = false;
    public boolean isLarge = false;
    public int height;
    public int width;

    public DynamicBanner() {
        this.tint.color.a = 0.0F;
        this.textTint.color.a = 0.0F;
    }

    public void appear() {
        appear(Y, this.label);
    }

    public void appear(String label) {
        appear(Y, label);
    }

    public void appearInstantly(String label) {
        appearInstantly(Y, label);
    }

    public void appear(float y, String label) {
        this.startY = y + Y_OFFSET;
        this.y = y + Y_OFFSET;
        this.targetY = y;
        this.label = label;
        this.scale = 0.25F;
        this.pressed = false;
        this.isMoving = true;
        this.show = true;
        this.animateTimer = 0.5F;
        this.tint.changeColor(IDLE_COLOR, 9.0F);
        this.textTint.changeColor(TEXT_SHOW_COLOR, 9.0F);
    }

    public void appearInstantly(float y, String label) {
        this.isMoving = false;
        this.animateTimer = 0.0F;
        this.y = y;
        this.targetY = y;
        this.scale = 1.2F;
        this.label = label;
        this.pressed = false;
        this.show = true;
        this.tint.changeColor(IDLE_COLOR, 9.0F);
        this.textTint.changeColor(TEXT_SHOW_COLOR, 9.0F);
    }

    public void hide() {
        this.show = false;
        this.isMoving = false;
        this.tint.changeColor(FADE_COLOR, 18.0F);
        this.textTint.changeColor(FADE_COLOR, 18.0F);
    }

    public void update() {
        this.tint.update();
        this.textTint.update();

        if (this.show) {

            this.animateTimer -= Gdx.graphics.getDeltaTime();
            if (this.animateTimer < 0.0F) {
                this.animateTimer = 0.0F;
                this.isMoving = false;
            } else {
                this.y = Interpolation.swingOut.apply(this.startY, this.targetY, (0.5F - this.animateTimer) * 2.0F);
                this.scale = Interpolation.swingOut.apply(0.0F, 1.2F, (0.5F - this.animateTimer) * 2.0F);
                if (this.scale <= 0.0F) {
                    this.scale = 0.01F;
                }
            }

            this.tint.changeColor(IDLE_COLOR, 9.0F);
        }
    }

    public void render(SpriteBatch sb) {
        if (this.textTint.color.a != 0.0F && this.label != null) {
            sb.setColor(this.tint.color);
            sb.draw(ImageMaster.VICTORY_BANNER, Settings.WIDTH / 2.0F - 556.0F, this.y - 119.0F, 556.0F, 119.0F,
                    1112.0F, 238.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1112, 238, false, false);

            FontHelper.renderFontCentered(sb, FontHelper.losePowerFont, this.label, Settings.WIDTH / 2.0F,
                    this.y + 22.0F * Settings.scale, this.textTint.color, this.scale);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * DynamicBanner.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

