package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class InteractableTorchEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float particleTimer1 = 0.0F;
    private Hitbox hb;
    private boolean activated = true;
    private static final float PARTICLE_EMIT_INTERVAL = 0.1F;
    private static TextureAtlas.AtlasRegion img;
    private TorchSize size = TorchSize.M;

    public InteractableTorchEffect(float x, float y, TorchSize size) {
        if (img == null) {
            img = ImageMaster.vfxAtlas.findRegion("env/torch");
        }
        this.size = size;
        this.x = x;
        this.y = y;

        if (Settings.isLetterbox) {
            this.y += Settings.LETTERBOX_OFFSET_Y;
        }
        this.hb = new Hitbox(50.0F * Settings.scale, 60.0F * Settings.scale);
        this.hb.move(x, this.y);
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.4F);

        switch (size) {
            case S:
                this.scale = Settings.scale * 0.6F;
                break;
            case M:
                this.scale = Settings.scale;
                break;
            case L:
                this.scale = Settings.scale * 1.4F;
                break;
        }
    }

    public InteractableTorchEffect(float x, float y) {
        this(x, y, TorchSize.M);
    }

    public enum TorchSize {
        S, M, L;
    }

    public void update() {
        this.hb.update();
        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.activated = !this.activated;
            if (this.activated) {
                CardCrawlGame.sound.playA("ATTACK_FIRE", 0.4F);
            } else {
                CardCrawlGame.sound.play("SCENE_TORCH_EXTINGUISH");
            }
        }

        if (this.activated && !Settings.DISABLE_EFFECTS) {
            this.particleTimer1 -= Gdx.graphics.getDeltaTime();
            if (this.particleTimer1 < 0.0F) {
                this.particleTimer1 = 0.1F;
                switch (this.size) {
                    case S:
                        AbstractDungeon.effectsQueue
                                .add(new TorchParticleSEffect(this.x, this.y - 10.0F * Settings.scale));
                        AbstractDungeon.effectsQueue
                                .add(new LightFlareSEffect(this.x, this.y - 10.0F * Settings.scale));
                        break;
                    case M:
                        AbstractDungeon.effectsQueue.add(new TorchParticleMEffect(this.x, this.y));
                        AbstractDungeon.effectsQueue.add(new LightFlareMEffect(this.x, this.y));
                        break;
                    case L:
                        AbstractDungeon.effectsQueue
                                .add(new TorchParticleLEffect(this.x, this.y + 14.0F * Settings.scale));
                        AbstractDungeon.effectsQueue
                                .add(new LightFlareLEffect(this.x, this.y + 14.0F * Settings.scale));
                        break;
                }
            }
        }
    }

    public void render(SpriteBatch sb) {
        if (Settings.DISABLE_EFFECTS) {
            return;
        }

        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x - (img.packedWidth / 2),
                this.y - (img.packedHeight / 2) - 24.0F * Settings.yScale, img.packedWidth / 2.0F,
                img.packedHeight / 2.0F, img.packedWidth, img.packedHeight, this.scale, this.scale, this.rotation);

        this.hb.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * InteractableTorchEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */
