package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class OrbFlareEffect
        extends AbstractGameEffect {
    private static TextureAtlas.AtlasRegion outer;
    private static TextureAtlas.AtlasRegion inner;
    private float scaleY;
    private static final float DUR = 0.5F;
    private AbstractOrb orb;
    private OrbFlareColor flareColor;
    private Color color2;

    public enum OrbFlareColor {
        LIGHTNING, DARK, PLASMA, FROST;
    }

    public OrbFlareEffect(AbstractOrb orb, OrbFlareColor setColor) {
        if (outer == null) {
            outer = ImageMaster.vfxAtlas.findRegion("combat/orbFlareOuter");
            inner = ImageMaster.vfxAtlas.findRegion("combat/orbFlareInner");
        }

        this.orb = orb;
        this.renderBehind = true;
        this.duration = 0.5F;
        this.startingDuration = 0.5F;
        this.flareColor = setColor;
        setColor();
        this.scale = 2.0F * Settings.scale;
        this.scaleY = 0.0F;
    }

    private void setColor() {
        switch (this.flareColor) {
            case DARK:
                this.color = Color.VIOLET.cpy();
                this.color2 = Color.BLACK.cpy();
                break;
            case FROST:
                this.color = Settings.BLUE_TEXT_COLOR.cpy();
                this.color2 = Color.LIGHT_GRAY.cpy();
                break;
            case LIGHTNING:
                this.color = Color.CHARTREUSE.cpy();
                this.color2 = Color.WHITE.cpy();
                break;
            case PLASMA:
                this.color = Color.CORAL.cpy();
                this.color2 = Color.CYAN.cpy();
                break;
        }
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.duration = 0.0F;
            this.isDone = true;
        }

        this.scaleY = Interpolation.elasticIn.apply(2.2F, 0.8F, this.duration * 2.0F);
        this.scale = Interpolation.elasticIn.apply(2.1F, 1.9F, this.duration * 2.0F);
        this.color.a = Interpolation.pow2Out.apply(0.0F, 0.6F, this.duration * 2.0F);
        this.color2.a = this.color.a;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color2);
        sb.draw((TextureRegion) inner, this.orb.cX - inner.packedWidth / 2.0F, this.orb.cY - inner.packedHeight / 2.0F,
                inner.packedWidth / 2.0F, inner.packedHeight / 2.0F, inner.packedWidth, inner.packedHeight,
                this.scale * Settings.scale * 1.1F, this.scaleY * Settings.scale,

                MathUtils.random(-1.0F, 1.0F));
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw((TextureRegion) outer, this.orb.cX - outer.packedWidth / 2.0F, this.orb.cY - outer.packedHeight / 2.0F,
                outer.packedWidth / 2.0F, outer.packedHeight / 2.0F, outer.packedWidth, outer.packedHeight, this.scale,
                this.scaleY * Settings.scale,

                MathUtils.random(-2.0F, 2.0F));
        sb.draw((TextureRegion) outer, this.orb.cX - outer.packedWidth / 2.0F, this.orb.cY - outer.packedHeight / 2.0F,
                outer.packedWidth / 2.0F, outer.packedHeight / 2.0F, outer.packedWidth, outer.packedHeight, this.scale,
                this.scaleY * Settings.scale,

                MathUtils.random(-2.0F, 2.0F));
        sb.setBlendFunction(770, 771);
        sb.setColor(this.color2);
        sb.draw((TextureRegion) inner, this.orb.cX - inner.packedWidth / 2.0F, this.orb.cY - inner.packedHeight / 2.0F,
                inner.packedWidth / 2.0F, inner.packedHeight / 2.0F, inner.packedWidth, inner.packedHeight,
                this.scale * Settings.scale * 1.1F, this.scaleY * Settings.scale,

                MathUtils.random(-1.0F, 1.0F));
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * OrbFlareEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
