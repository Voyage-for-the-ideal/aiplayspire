package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class MoveNameEffect extends AbstractGameEffect {
    private static final float TEXT_DURATION = 2.5F;
    private static final float STARTING_OFFSET_Y = 100.0F * Settings.scale;
    private static final float TARGET_OFFSET_Y = 120.0F * Settings.scale;
    private float x;
    private float y;
    private Color color2 = Color.BLACK.cpy();
    private float offsetY;
    private String msg;
    private TextureAtlas.AtlasRegion img;

    public MoveNameEffect(float x, float y, String msg) {
        this.img = ImageMaster.MOVE_NAME_BG;
        this.duration = 2.5F;
        this.startingDuration = 2.5F;
        if (msg == null) {
            this.isDone = true;
        } else {
            this.msg = msg;
        }
        this.x = x;
        this.y = y;
        this.color = Settings.CREAM_COLOR.cpy();
    }

    public void update() {
        this.offsetY = Interpolation.exp10In.apply(TARGET_OFFSET_Y, STARTING_OFFSET_Y, this.duration / 2.5F);
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration > 2.0F) {
            this.color.a = Interpolation.fade.apply(1.0F - (this.duration - 2.0F) / 0.5F);
            this.color2.a = this.color.a;
        } else if (this.duration < 1.0F) {
            this.color.a = Interpolation.fade.apply(this.duration);
            this.color2.a = this.color.a;
        } else {
            this.color.a = 1.0F;
            this.color2.a = 1.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color2);
        sb.draw((TextureRegion) this.img, this.x - this.img.packedWidth / 2.0F,
                this.y - this.img.packedHeight / 2.0F + this.offsetY, this.img.packedWidth / 2.0F,
                this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, this.scale, this.scale,
                this.rotation);

        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, this.msg, this.x, this.y + this.offsetY, this.color,
                1.0F);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * MoveNameEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
