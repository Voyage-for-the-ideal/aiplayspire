package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class ShopSpeechBubble extends AbstractGameEffect {
    private static final int RAW_W = 512;
    private static final float SHADOW_OFFSET = 16.0F * Settings.scale;
    private static final float WAVY_DISTANCE = 2.0F * Settings.scale;
    private static final float ADJUST_X = 170.0F * Settings.scale;
    private static final float ADJUST_Y = 116.0F * Settings.scale;

    public static final float FADE_TIME = 0.3F;
    private float shadow_offset = 0.0F;
    private float x;
    private float y;
    private float scaleTimer = 0.3F;
    private float wavy_y;
    private float wavyHelper;
    private boolean facingRight;
    public Hitbox hb;
    private Color shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);

    public ShopSpeechBubble(float x, float y, String msg, boolean isPlayer) {
        this(x, y, 2.0F, msg, isPlayer);
    }

    public ShopSpeechBubble(float x, float y, float duration, String msg, boolean isPlayer) {
        if (isPlayer) {
            this.x = x + ADJUST_X;
        } else {
            this.x = x - ADJUST_X;
        }

        this.y = y + ADJUST_Y;
        this.scale = Settings.scale / 2.0F;
        this.color = new Color(0.8F, 0.9F, 0.9F, 0.0F);
        this.duration = duration;
        this.facingRight = !isPlayer;

        if (!this.facingRight) {
            this.hb = new Hitbox(x, y, 350.0F * Settings.scale, 270.0F * Settings.scale);
        } else {
            this.hb = new Hitbox(x - 350.0F * Settings.scale, y, 350.0F * Settings.scale, 270.0F * Settings.scale);
        }
    }

    public void update() {
        updateScale();
        this.hb.update();

        this.wavyHelper += Gdx.graphics.getDeltaTime() * 4.0F;
        this.wavy_y = MathUtils.sin(this.wavyHelper) * WAVY_DISTANCE;

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        if (this.duration > 0.3F) {
            this.color.a = MathUtils.lerp(this.color.a, 1.0F, Gdx.graphics.getDeltaTime() * 12.0F);
        } else {
            this.color.a = MathUtils.lerp(this.color.a, 0.0F, Gdx.graphics.getDeltaTime() * 12.0F);
        }
        this.shadow_offset = MathUtils.lerp(this.shadow_offset, SHADOW_OFFSET, Gdx.graphics.getDeltaTime() * 4.0F);
    }

    private void updateScale() {
        this.scaleTimer -= Gdx.graphics.getDeltaTime();
        if (this.scaleTimer < 0.0F) {
            this.scaleTimer = 0.0F;
        }

        if (Settings.isMobile) {
            this.scale = Interpolation.swingIn.apply(Settings.scale * 1.15F, Settings.scale / 2.0F,
                    this.scaleTimer / 0.3F);
        } else {
            this.scale = Interpolation.swingIn.apply(Settings.scale, Settings.scale / 2.0F, this.scaleTimer / 0.3F);
        }
    }

    public void render(SpriteBatch sb) {
        this.color.a /= 4.0F;
        sb.setColor(this.shadowColor);
        sb.draw(ImageMaster.SHOP_SPEECH_BUBBLE_IMG, this.x - 256.0F + this.shadow_offset,
                this.y - 256.0F - this.shadow_offset + this.wavy_y, 256.0F, 256.0F, 512.0F, 512.0F, this.scale,
                this.scale, this.rotation, 0, 0, 512, 512, this.facingRight, false);

        sb.setColor(this.color);
        sb.draw(ImageMaster.SHOP_SPEECH_BUBBLE_IMG, this.x - 256.0F, this.y - 256.0F + this.wavy_y, 256.0F, 256.0F,
                512.0F, 512.0F, this.scale, this.scale, this.rotation, 0, 0, 512, 512, this.facingRight, false);

        this.hb.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * ShopSpeechBubble.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
