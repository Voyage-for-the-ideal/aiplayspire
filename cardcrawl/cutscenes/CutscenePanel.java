package com.megacrit.cardcrawl.cutscenes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.ScreenShake;

public class CutscenePanel {
    private Texture img;
    private Color color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    public boolean activated = false;
    private String sfx = null;
    public boolean finished = false;
    public boolean fadeOut = false;

    public CutscenePanel(String imgUrl, String sfx) {
        this.img = ImageMaster.loadImage(imgUrl);
        this.sfx = sfx;
    }

    public CutscenePanel(String imgUrl) {
        this(imgUrl, null);
    }

    public void update() {
        if (this.fadeOut) {
            this.color.a -= Gdx.graphics.getDeltaTime();
            if (this.color.a < 0.0F) {
                this.color.a = 0.0F;
                this.finished = true;
            }

            return;
        }
        if (this.activated && !this.finished) {
            if (this.sfx != null) {
                this.color.a += Gdx.graphics.getDeltaTime() * 10.0F;
            } else {
                this.color.a += Gdx.graphics.getDeltaTime();
            }

            if (this.color.a > 1.0F) {
                this.color.a = 1.0F;
                this.finished = true;
            }
        }
    }

    public void activate() {
        if (this.sfx != null) {
            CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.SHORT, false);
            CardCrawlGame.sound.play(this.sfx);
            CardCrawlGame.sound.playA(this.sfx, -0.2F);
        }
        this.activated = true;
    }

    public void render(SpriteBatch sb) {
        if (this.img != null) {
            sb.setColor(this.color);
            if (Settings.isSixteenByTen) {
                sb.draw(this.img, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
            } else {
                sb.draw(this.img, 0.0F, -50.0F * Settings.scale, Settings.WIDTH,
                        Settings.HEIGHT + 110.0F * Settings.scale);
            }
        }
    }

    public void fadeOut() {
        this.fadeOut = true;
        this.finished = false;
    }

    public void dispose() {
        if (this.img != null) {
            this.img.dispose();
            this.img = null;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cutscenes\
 * CutscenePanel.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

