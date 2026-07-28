package com.megacrit.cardcrawl.screens.splash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class SplashScreen {
    private Texture img;
    private float timer = 0.0F;
    private static final float BOUNCE_DUR = 1.2F;
    private static final float FADE_DUR = 3.0F;
    private static final float WAIT_DUR = 1.5F;
    private Color color = new Color(1.0F, 1.0F, 1.0F, 0.0F), bgColor = new Color(0.0F, 0.0F, 0.0F, 1.0F);
    private static final float FADE_OUT_DUR = 1.0F;
    private static final int W = 512;
    private static final int H = 512;
    private Color shadowColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private Phase phase = Phase.INIT;

    public boolean isDone = false;
    private static final float OFFSET_Y = 8.0F * Settings.scale;
    private static final float OFFSET_X = 12.0F * Settings.scale;
    private float x = Settings.WIDTH / 2.0F, y = Settings.HEIGHT / 2.0F - OFFSET_Y;
    private float sX = Settings.WIDTH / 2.0F;
    private float sY = Settings.HEIGHT / 2.0F;
    private Color cream = Color.valueOf("ffffdbff");
    private Color bgBlue = Color.valueOf("074254ff");
    private boolean playSfx = false;
    private long sfxId = -1L;
    private String sfxKey = null;

    private enum Phase {
        INIT, BOUNCE, FADE, WAIT, FADE_OUT;
    }

    public SplashScreen() {
        this.img = ImageMaster.loadImage("images/megaCrit.png");
    }

    public void update() {
        if ((InputHelper.justClickedLeft || CInputActionSet.select.isJustPressed()) &&
                this.phase != Phase.FADE_OUT) {
            this.phase = Phase.FADE_OUT;
            this.timer = 1.0F;

            if (this.sfxKey != null) {
                CardCrawlGame.sound.fadeOut(this.sfxKey, this.sfxId);
            }
        }

        switch (this.phase) {
            case INIT:
                this.timer -= Gdx.graphics.getDeltaTime();
                if (this.timer < 0.0F) {
                    this.phase = Phase.BOUNCE;
                    this.timer = 1.2F;
                }
                break;
            case BOUNCE:
                this.timer -= Gdx.graphics.getDeltaTime();
                this.color.a = Interpolation.fade.apply(1.0F, 0.0F, this.timer / 1.2F);
                this.y = Interpolation.elasticIn.apply(Settings.HEIGHT / 2.0F,
                        Settings.HEIGHT / 2.0F - 200.0F * Settings.scale, this.timer / 1.2F);

                if (this.timer < 0.96000004F && !this.playSfx) {
                    this.playSfx = true;
                    this.sfxId = CardCrawlGame.sound.play("SPLASH");
                }

                if (this.timer < 0.0F) {
                    this.phase = Phase.FADE;
                    this.timer = 3.0F;
                }
                break;
            case FADE:
                this.timer -= Gdx.graphics.getDeltaTime();
                this.color.r = Interpolation.fade.apply(this.cream.r, 1.0F, this.timer / 3.0F);
                this.color.g = Interpolation.fade.apply(this.cream.g, 1.0F, this.timer / 3.0F);
                this.color.b = Interpolation.fade.apply(this.cream.b, 1.0F, this.timer / 3.0F);
                this.bgColor.r = Interpolation.fade.apply(this.bgBlue.r, 0.0F, this.timer / 3.0F);
                this.bgColor.g = Interpolation.fade.apply(this.bgBlue.g, 0.0F, this.timer / 3.0F);
                this.bgColor.b = Interpolation.fade.apply(this.bgBlue.b, 0.0F, this.timer / 3.0F);
                this.sX = Interpolation.exp5Out.apply(Settings.WIDTH / 2.0F + OFFSET_X, Settings.WIDTH / 2.0F,
                        this.timer / 3.0F);
                this.sY = Interpolation.exp5Out.apply(Settings.HEIGHT / 2.0F - OFFSET_Y, Settings.HEIGHT / 2.0F,
                        this.timer / 3.0F);

                if (this.timer < 0.0F) {
                    this.phase = Phase.WAIT;
                    this.timer = 1.5F;
                }
                break;
            case WAIT:
                this.timer -= Gdx.graphics.getDeltaTime();
                if (this.timer < 0.0F) {
                    this.phase = Phase.FADE_OUT;
                    this.timer = 1.0F;
                }
                break;
            case FADE_OUT:
                this.bgColor.a = Interpolation.fade.apply(0.0F, 1.0F, this.timer / 1.0F);
                this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.timer / 1.0F);
                this.timer -= Gdx.graphics.getDeltaTime();
                if (this.timer < 0.0F) {
                    this.img.dispose();
                    this.isDone = true;
                }
                break;
        }
    }
    public void render(SpriteBatch sb) {
        sb.setColor(this.bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        sb.setColor(this.shadowColor);
        sb.draw(this.img, this.sX - 256.0F, this.sY - 256.0F, 256.0F, 256.0F, 512.0F, 512.0F, Settings.scale,
                Settings.scale, 0.0F, 0, 0, 512, 512, false, false);

        Color s = new Color(0.0F, 0.0F, 0.0F, this.color.a / 5.0F);
        sb.setColor(s);
        sb.draw(this.img, this.x - 256.0F + 14.0F * Settings.scale, this.y - 256.0F - 14.0F * Settings.scale, 256.0F,
                256.0F, 512.0F, 512.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 512, false, false);

        sb.setColor(this.color);
        sb.draw(this.img, this.x - 256.0F, this.y - 256.0F, 256.0F, 256.0F, 512.0F, 512.0F, Settings.scale,
                Settings.scale, 0.0F, 0, 0, 512, 512, false, false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\splash\
 * SplashScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

