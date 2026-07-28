package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class TouchPickupGold extends AbstractGameEffect {
    private static final float RAW_IMG_WIDTH = 32.0F;
    private static final float IMG_WIDTH = 32.0F * Settings.scale;

    private TextureAtlas.AtlasRegion img;

    private boolean isPickupable = false;
    public boolean pickedup = false;
    private float x;
    private float y;
    private float vY = -0.2F;
    private float landingY;
    private boolean willBounce;
    private boolean hasBounced = true;
    private float bounceY;
    private float bounceX;
    private float vX = 0.0F;
    private float gravity;
    private Hitbox hitbox;

    public TouchPickupGold() {
        this.gravity = -0.3F;
        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.COPPER_COIN_1;
        } else {
            this.img = ImageMaster.COPPER_COIN_2;
        }

        this.willBounce = (MathUtils.random(3) != 0);
        if (this.willBounce) {
            this.hasBounced = false;
            this.bounceY = MathUtils.random(1.0F, 4.0F);
            this.bounceX = MathUtils.random(-3.0F, 3.0F);
        }

        this.y = Settings.HEIGHT * MathUtils.random(1.1F, 1.3F) - this.img.packedHeight / 2.0F;
        this.x = MathUtils.random(Settings.WIDTH * 0.3F, Settings.WIDTH * 0.95F) - this.img.packedWidth / 2.0F;
        this.landingY = MathUtils.random(AbstractDungeon.floorY - Settings.HEIGHT * 0.05F,
                AbstractDungeon.floorY + Settings.HEIGHT * 0.08F);

        this.rotation = MathUtils.random(360.0F);
        this.scale = Settings.scale;
    }

    public TouchPickupGold(boolean centerOnPlayer) {
        this();
        if (centerOnPlayer) {
            this.x = MathUtils.random(AbstractDungeon.player.drawX - AbstractDungeon.player.hb_w,
                    AbstractDungeon.player.drawX + AbstractDungeon.player.hb_w);

            this.gravity = -0.7F;
        }
    }

    public void update() {
        if (!this.isPickupable) {
            this.x += this.vX * Gdx.graphics.getDeltaTime() * 60.0F;
            this.y += this.vY * Gdx.graphics.getDeltaTime() * 60.0F;
            this.vY += this.gravity;

            if (this.y < this.landingY) {
                if (this.hasBounced) {
                    this.y = this.landingY;
                    this.isPickupable = true;
                    this.hitbox = new Hitbox(this.x - IMG_WIDTH * 2.0F, this.y - IMG_WIDTH * 2.0F, IMG_WIDTH * 4.0F,
                            IMG_WIDTH * 4.0F);
                } else {
                    if (MathUtils.random(1) == 0) {
                        this.hasBounced = true;
                    }
                    this.y = this.landingY;
                    this.vY = this.bounceY;
                    this.vX = this.bounceX;
                    this.bounceY *= 0.5F;
                    this.bounceX *= 0.3F;
                }

            }
        } else if (!this.pickedup) {
            this.pickedup = true;
            this.isDone = true;
            playGainGoldSFX();

            AbstractDungeon.effectsQueue.add(new ShineLinesEffect(this.x, this.y));
        }
    }

    private void playGainGoldSFX() {
        int roll = MathUtils.random(2);
        switch (roll) {
            case 0:
                CardCrawlGame.sound.play("GOLD_GAIN", 0.1F);
                return;
            case 1:
                CardCrawlGame.sound.play("GOLD_GAIN_3", 0.1F);
                return;
        }
        CardCrawlGame.sound.play("GOLD_GAIN_5", 0.1F);
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        if (this.hitbox != null)
            this.hitbox.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * TouchPickupGold.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
