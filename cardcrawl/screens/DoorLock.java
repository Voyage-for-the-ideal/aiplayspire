package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.DoorFlashEffect;

public class DoorLock {
    private Color glowColor = Color.WHITE.cpy();
    private Texture lockImg = null;
    private boolean glowing;
    private boolean unlockAnimation = false;
    private Texture glowImg = null;
    private boolean usedFlash = false;
    private float x = 0.0F;
    private float y;
    private float unlockTimer = 2.0F;
    private float startY;
    private float targetY;
    private LockColor c;

    public enum LockColor {
        RED, GREEN, BLUE;
    }

    public DoorLock(LockColor c, boolean glowing, boolean eventVersion) {
        this.c = c;
        this.glowing = glowing;

        if (eventVersion) {
            this.startY = -48.0F * Settings.scale;
        } else {
            this.startY = 0.0F * Settings.scale;
        }
        this.y = this.startY;

        switch (c) {
            case RED:
                this.lockImg = ImageMaster.loadImage("images/ui/door/lock_red.png");
                this.glowImg = ImageMaster.loadImage("images/ui/door/glow_red.png");
                glowing = CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.IRONCLAD.name() + "_WIN",
                        false);
                if (eventVersion) {
                    this.targetY = -748.0F * Settings.scale;
                    break;
                }
                this.targetY = -700.0F * Settings.scale;
                break;

            case GREEN:
                this.lockImg = ImageMaster.loadImage("images/ui/door/lock_green.png");
                this.glowImg = ImageMaster.loadImage("images/ui/door/glow_green.png");
                glowing = CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.THE_SILENT.name() + "_WIN",
                        false);
                if (eventVersion) {
                    this.targetY = 752.0F * Settings.scale;
                    break;
                }
                this.targetY = 800.0F * Settings.scale;
                break;

            case BLUE:
                this.lockImg = ImageMaster.loadImage("images/ui/door/lock_blue.png");
                this.glowImg = ImageMaster.loadImage("images/ui/door/glow_blue.png");
                glowing = CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.DEFECT.name() + "_WIN", false);
                if (eventVersion) {
                    this.targetY = -748.0F * Settings.scale;
                    break;
                }
                this.targetY = -700.0F * Settings.scale;
                break;
        }
    }

    public void update() {
        updateUnlockAnimation();
    }

    private void updateUnlockAnimation() {
        if (this.unlockAnimation) {
            this.unlockTimer -= Gdx.graphics.getDeltaTime();
            if (this.unlockTimer < 0.0F) {
                this.unlockTimer = 0.0F;
                this.unlockAnimation = false;
            }

            switch (this.c) {
                case RED:
                    this.x = Interpolation.pow5In.apply(-1000.0F * Settings.scale, 0.0F, this.unlockTimer / 2.0F);
                    this.y = Interpolation.pow5In.apply(this.targetY, this.startY, this.unlockTimer / 2.0F);
                    break;
                case GREEN:
                    this.y = Interpolation.pow5In.apply(800.0F * Settings.scale, this.startY, this.unlockTimer / 2.0F);
                    break;
                case BLUE:
                    this.x = Interpolation.pow5In.apply(1000.0F * Settings.scale, 0.0F, this.unlockTimer / 2.0F);
                    this.y = Interpolation.pow5In.apply(this.targetY, this.startY, this.unlockTimer / 2.0F);
                    break;
            }
        }
    }

    public void unlock() {
        this.unlockAnimation = true;
        this.unlockTimer = 2.0F;
        this.x = 0.0F;
        this.y = this.startY;
    }

    public void render(SpriteBatch sb) {
        if (this.lockImg == null) {
            return;
        }

        renderLock(sb);
        renderGlow(sb);
    }

    private void renderLock(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(this.lockImg, Settings.WIDTH / 2.0F - 960.0F + this.x, Settings.HEIGHT / 2.0F - 600.0F + this.y, 960.0F,
                600.0F, 1920.0F, 1200.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1200, false, false);
    }

    private void renderGlow(SpriteBatch sb) {
        if (this.glowing) {
            this.glowColor.a = (MathUtils.cosDeg((float) (System.currentTimeMillis() / 4L % 360L)) + 3.0F) / 4.0F;
            sb.setColor(this.glowColor);
            sb.setBlendFunction(770, 1);
            sb.draw(this.glowImg, Settings.WIDTH / 2.0F - 960.0F + this.x, Settings.HEIGHT / 2.0F - 600.0F + this.y,
                    960.0F, 600.0F, 1920.0F, 1200.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1200, false,
                    false);

            sb.setBlendFunction(770, 771);
        }
    }

    public void reset() {
        this.usedFlash = false;
        this.unlockAnimation = false;
        this.x = 0.0F;
        this.y = this.startY;
        this.unlockTimer = 2.0F;
    }

    public void flash(boolean eventVersion) {
        if (!this.usedFlash) {
            CardCrawlGame.sound.playA("ATTACK_MAGIC_SLOW_2", 1.0F);
            this.usedFlash = true;
            CardCrawlGame.mainMenuScreen.doorUnlockScreen.effects.add(new DoorFlashEffect(this.glowImg, eventVersion));
        }
    }

    public void dispose() {
        this.lockImg.dispose();
        this.glowImg.dispose();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\DoorLock.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

