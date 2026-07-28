package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.combat.StrikeEffect;

public class DamageHeartEffect extends AbstractGameEffect {
    private static int blockSound = 0;

    public TextureAtlas.AtlasRegion img;
    private float x;
    private float y;
    private float sY;
    private float delayTimer = 2.0F;
    private float tY;
    private static final float DURATION = 0.6F;
    private AbstractGameAction.AttackEffect effect;
    private boolean triggered = false;
    private int damage;

    public DamageHeartEffect(float delay, float x, float y, AbstractGameAction.AttackEffect effect, int damage) {
        this.duration = 0.6F;
        this.delayTimer = delay;
        this.startingDuration = 0.6F;
        this.effect = effect;
        this.img = loadImage();
        this.color = Color.WHITE.cpy();
        this.scale = Settings.scale;
        this.damage = damage;

        this.x = x - this.img.packedWidth / 2.0F + MathUtils.random(-150.0F, 150.0F) * Settings.scale;
        y -= this.img.packedHeight / 2.0F + MathUtils.random(-150.0F, 150.0F) * Settings.scale;

        switch (effect) {
            case SHIELD:
                this.y = y + 80.0F * Settings.scale;
                this.sY = this.y;
                this.tY = y;
                return;
        }
        this.y = y;
        this.sY = y;
        this.tY = y;
    }

    private TextureAtlas.AtlasRegion loadImage() {
        switch (this.effect) {
            case SLASH_DIAGONAL:
                return ImageMaster.ATK_SLASH_D;
            case SLASH_HEAVY:
                return ImageMaster.ATK_SLASH_HEAVY;
            case SLASH_HORIZONTAL:
                return ImageMaster.ATK_SLASH_H;
            case SLASH_VERTICAL:
                return ImageMaster.ATK_SLASH_V;
            case BLUNT_LIGHT:
                return ImageMaster.ATK_BLUNT_LIGHT;
            case BLUNT_HEAVY:
                this.rotation = MathUtils.random(360.0F);
                return ImageMaster.ATK_BLUNT_HEAVY;
            case FIRE:
                return ImageMaster.ATK_FIRE;
            case POISON:
                return ImageMaster.ATK_POISON;
            case SHIELD:
                return ImageMaster.ATK_SHIELD;
            case NONE:
                return null;
        }
        return ImageMaster.ATK_SLASH_D;
    }

    private void playSound(AbstractGameAction.AttackEffect effect) {
        switch (effect) {
            case SLASH_HEAVY:
                CardCrawlGame.sound.play("ATTACK_HEAVY");

            case BLUNT_LIGHT:
                CardCrawlGame.sound.play("BLUNT_FAST");

            case BLUNT_HEAVY:
                CardCrawlGame.sound.play("BLUNT_HEAVY");

            case FIRE:
                CardCrawlGame.sound.play("ATTACK_FIRE");

            case POISON:
                CardCrawlGame.sound.play("ATTACK_POISON");

            case SHIELD:
                playBlockSound();

            case NONE:
                return;
        }
        CardCrawlGame.sound.play("ATTACK_FAST");
    }

    private void playBlockSound() {
        if (blockSound == 0) {
            CardCrawlGame.sound.play("BLOCK_GAIN_1");
        } else if (blockSound == 1) {
            CardCrawlGame.sound.play("BLOCK_GAIN_2");
        } else {
            CardCrawlGame.sound.play("BLOCK_GAIN_3");
        }

        blockSound++;
        if (blockSound > 2) {
            blockSound = 0;
        }
    }

    public void update() {
        if (this.delayTimer > 0.0F) {
            this.delayTimer -= Gdx.graphics.getDeltaTime();
            if (this.delayTimer < 0.0F) {
                playSound(this.effect);
                AbstractDungeon.effectsQueue.add(new StrikeEffect(null, this.x + this.img.packedWidth / 2.0F,
                        this.y + this.img.packedHeight / 2.0F, this.damage));
            }

            return;
        }

        switch (this.effect) {
            case SHIELD:
                this.duration -= Gdx.graphics.getDeltaTime();
                if (this.duration < 0.0F) {
                    this.isDone = true;
                    this.color.a = 0.0F;
                } else if (this.duration < 0.2F) {
                    this.color.a = this.duration * 5.0F;
                } else {
                    this.color.a = Interpolation.fade.apply(1.0F, 0.0F, this.duration * 0.75F / 0.6F);
                }

                this.y = Interpolation.exp10In.apply(this.tY, this.sY, this.duration / 0.6F);

                if (this.duration < 0.4F && !this.triggered) {
                    this.triggered = true;
                }
                return;
        }
        super.update();
    }

    public void render(SpriteBatch sb) {
        if (this.delayTimer < 0.0F) {
            sb.setColor(this.color);
            sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                    this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
        }
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * DamageHeartEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
