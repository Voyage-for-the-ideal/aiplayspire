package com.megacrit.cardcrawl.vfx.cardManip;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class CardFlashVfx extends AbstractGameEffect {
    private AbstractCard card;
    private TextureAtlas.AtlasRegion img;
    private float yScale = 0.0F;
    private boolean isSuper = false;

    public CardFlashVfx(AbstractCard card, boolean isSuper) {
        this(card, new Color(1.0F, 0.8F, 0.2F, 0.0F), isSuper);
    }

    public CardFlashVfx(AbstractCard card, Color c, boolean isSuper) {
        this.card = card;
        this.isSuper = isSuper;
        this.duration = 0.5F;

        if (isSuper) {
            this.img = ImageMaster.CARD_FLASH_VFX;
        } else {
            switch (card.type) {
                case POWER:
                    this.img = ImageMaster.CARD_POWER_BG_SILHOUETTE;
                    break;
                case ATTACK:
                    this.img = ImageMaster.CARD_ATTACK_BG_SILHOUETTE;
                    break;
                default:
                    this.img = ImageMaster.CARD_SKILL_BG_SILHOUETTE;
                    break;
            }
        }
        this.color = c;
    }

    public CardFlashVfx(AbstractCard card) {
        this(card, new Color(1.0F, 0.8F, 0.2F, 0.0F), false);
    }

    public CardFlashVfx(AbstractCard card, Color c) {
        this.card = card;
        this.duration = 0.5F;

        switch (card.type) {
            case POWER:
                this.img = ImageMaster.CARD_POWER_BG_SILHOUETTE;
                break;
            case ATTACK:
                this.img = ImageMaster.CARD_ATTACK_BG_SILHOUETTE;
                break;
            default:
                this.img = ImageMaster.CARD_SKILL_BG_SILHOUETTE;
                break;
        }
        this.color = c;
        this.isSuper = false;
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else {
            this.yScale = Interpolation.bounceIn.apply(1.2F, 1.1F, this.duration * 2.0F);
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        this.color.a = this.duration;
        sb.setColor(this.color);

        if (this.isSuper) {
            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.52F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.53F * Settings.scale, this.card.angle);

            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.55F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.57F * Settings.scale, this.card.angle);

            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.58F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.6F * Settings.scale, this.card.angle);

        } else {

            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.52F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.52F * Settings.scale, this.card.angle);

            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.55F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.55F * Settings.scale, this.card.angle);

            sb.draw((TextureRegion) this.img, this.card.current_x + this.img.offsetX - this.img.originalWidth / 2.0F,
                    this.card.current_y + this.img.offsetY - this.img.originalHeight / 2.0F,
                    this.img.originalWidth / 2.0F - this.img.offsetX, this.img.originalHeight / 2.0F - this.img.offsetY,
                    this.img.packedWidth, this.img.packedHeight,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.58F * Settings.scale,
                    this.card.drawScale * (this.yScale + 1.0F) * 0.58F * Settings.scale, this.card.angle);
        }

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\cardManip\
 * CardFlashVfx.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
