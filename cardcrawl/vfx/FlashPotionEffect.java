package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.potions.AbstractPotion;

public class FlashPotionEffect
        extends AbstractGameEffect {
    private static final int W = 64;
    private static final float DURATION = 1.0F;
    private static final float END_SCALE = 8.0F * Settings.scale;

    private Texture containerImg;
    private Texture liquidImg;

    public FlashPotionEffect(AbstractPotion p) {
        this.duration = 1.0F;
        this.liquidColor = p.liquidColor.cpy();
        if (p.hybridColor != null) {
            this.renderHybrid = true;
            this.hybridColor = p.hybridColor.cpy();
        }
        if (p.spotsColor != null) {
            this.renderSpots = true;
            this.spotsColor = p.spotsColor.cpy();
        }
        this.color = Color.WHITE.cpy();

        switch (p.size) {
            case T:
                this.containerImg = ImageMaster.POTION_T_CONTAINER;
                this.liquidImg = ImageMaster.POTION_T_LIQUID;
                this.hybridImg = ImageMaster.POTION_T_HYBRID;
                this.spotsImg = ImageMaster.POTION_T_SPOTS;
                break;
            case S:
                this.containerImg = ImageMaster.POTION_S_CONTAINER;
                this.liquidImg = ImageMaster.POTION_S_LIQUID;
                this.hybridImg = ImageMaster.POTION_S_HYBRID;
                this.spotsImg = ImageMaster.POTION_S_SPOTS;
                break;
            case M:
                this.containerImg = ImageMaster.POTION_M_CONTAINER;
                this.liquidImg = ImageMaster.POTION_M_LIQUID;
                this.hybridImg = ImageMaster.POTION_M_HYBRID;
                this.spotsImg = ImageMaster.POTION_M_SPOTS;
                break;
            case SPHERE:
                this.containerImg = ImageMaster.POTION_SPHERE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SPHERE_LIQUID;
                this.hybridImg = ImageMaster.POTION_SPHERE_HYBRID;
                this.spotsImg = ImageMaster.POTION_SPHERE_SPOTS;
                break;
            case H:
                this.containerImg = ImageMaster.POTION_H_CONTAINER;
                this.liquidImg = ImageMaster.POTION_H_LIQUID;
                this.hybridImg = ImageMaster.POTION_H_HYBRID;
                this.spotsImg = ImageMaster.POTION_H_SPOTS;
                break;
            case BOTTLE:
                this.containerImg = ImageMaster.POTION_BOTTLE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_BOTTLE_LIQUID;
                this.hybridImg = ImageMaster.POTION_BOTTLE_HYBRID;
                this.spotsImg = ImageMaster.POTION_BOTTLE_SPOTS;
                break;
            case HEART:
                this.containerImg = ImageMaster.POTION_HEART_CONTAINER;
                this.liquidImg = ImageMaster.POTION_HEART_LIQUID;
                this.hybridImg = ImageMaster.POTION_HEART_HYBRID;
                this.spotsImg = ImageMaster.POTION_HEART_SPOTS;
                break;
            case SNECKO:
                this.containerImg = ImageMaster.POTION_SNECKO_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SNECKO_LIQUID;
                this.hybridImg = ImageMaster.POTION_SNECKO_HYBRID;
                this.spotsImg = ImageMaster.POTION_SNECKO_SPOTS;
                break;
            case FAIRY:
                this.containerImg = ImageMaster.POTION_FAIRY_CONTAINER;
                this.liquidImg = ImageMaster.POTION_FAIRY_LIQUID;
                this.hybridImg = ImageMaster.POTION_FAIRY_HYBRID;
                this.spotsImg = ImageMaster.POTION_FAIRY_SPOTS;
                break;
            case GHOST:
                this.containerImg = ImageMaster.POTION_GHOST_CONTAINER;
                this.liquidImg = ImageMaster.POTION_GHOST_LIQUID;
                this.hybridImg = ImageMaster.POTION_GHOST_HYBRID;
                this.spotsImg = ImageMaster.POTION_GHOST_SPOTS;
                break;
            case JAR:
                this.containerImg = ImageMaster.POTION_JAR_CONTAINER;
                this.liquidImg = ImageMaster.POTION_JAR_LIQUID;
                this.hybridImg = ImageMaster.POTION_JAR_HYBRID;
                this.spotsImg = ImageMaster.POTION_JAR_SPOTS;
                break;
            case BOLT:
                this.containerImg = ImageMaster.POTION_BOLT_CONTAINER;
                this.liquidImg = ImageMaster.POTION_BOLT_LIQUID;
                this.hybridImg = ImageMaster.POTION_BOLT_HYBRID;
                this.spotsImg = ImageMaster.POTION_BOLT_SPOTS;
                break;
            case CARD:
                this.containerImg = ImageMaster.POTION_CARD_CONTAINER;
                this.liquidImg = ImageMaster.POTION_CARD_LIQUID;
                this.hybridImg = ImageMaster.POTION_CARD_HYBRID;
                this.spotsImg = ImageMaster.POTION_CARD_SPOTS;
                break;
            case MOON:
                this.containerImg = ImageMaster.POTION_MOON_CONTAINER;
                this.liquidImg = ImageMaster.POTION_MOON_LIQUID;
                this.hybridImg = ImageMaster.POTION_MOON_HYBRID;
                break;
            case SPIKY:
                this.containerImg = ImageMaster.POTION_SPIKY_CONTAINER;
                this.liquidImg = ImageMaster.POTION_SPIKY_LIQUID;
                this.hybridImg = ImageMaster.POTION_SPIKY_HYBRID;
                break;
            case EYE:
                this.containerImg = ImageMaster.POTION_EYE_CONTAINER;
                this.liquidImg = ImageMaster.POTION_EYE_LIQUID;
                this.hybridImg = ImageMaster.POTION_EYE_HYBRID;
                break;
            case ANVIL:
                this.containerImg = ImageMaster.POTION_ANVIL_CONTAINER;
                this.liquidImg = ImageMaster.POTION_ANVIL_LIQUID;
                this.hybridImg = ImageMaster.POTION_ANVIL_HYBRID;
                break;
        }
    }
    private Texture hybridImg;
    private Texture spotsImg;
    private Color liquidColor;
    private Color color;

    public void update() {
        this.scale = Interpolation.exp10In.apply(Settings.scale, END_SCALE * 2.0F, this.duration / 1.0F);

        if (this.duration > 0.3F) {
            this.liquidColor.a = Interpolation.pow2.apply(0.4F, 0.05F, this.duration / 1.0F);
        } else {
            this.liquidColor.a = this.duration * 2.0F;
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        this.color.a = this.liquidColor.a;
        if (this.renderHybrid) {
            this.hybridColor.a = this.liquidColor.a;
        }
        if (this.renderSpots) {
            this.spotsColor.a = this.liquidColor.a;
        }

        if (this.duration < 0.0F)
            this.isDone = true;
    }

    private Color hybridColor;

    public void render(SpriteBatch sb, float x, float y) {
        sb.setColor(this.color);
        sb.draw(this.containerImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0,
                64, 64, false, false);
        sb.setColor(this.liquidColor);
        sb.draw(this.liquidImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0,
                64, 64, false, false);
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw(this.containerImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0,
                64, 64, false, false);
        sb.setColor(this.liquidColor);
        sb.draw(this.liquidImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0,
                64, 64, false, false);

        if (this.renderHybrid) {
            sb.setColor(this.hybridColor);
            sb.draw(this.hybridImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0,
                    0, 64, 64, false, false);
        }

        if (this.renderSpots) {
            sb.setColor(this.spotsColor);
            sb.draw(this.spotsImg, x - 32.0F, y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0,
                    64, 64, false, false);
        }

        sb.setBlendFunction(770, 771);
    }

    private Color spotsColor;
    private boolean renderHybrid = false, renderSpots = false;

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * FlashPotionEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
