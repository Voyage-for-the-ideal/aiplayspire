package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.megacrit.cardcrawl.core.Settings;

public abstract class AbstractGameEffect
        implements Disposable {
    public float duration;
    public float startingDuration;
    protected float scale = Settings.scale;
    protected Color color;
    public boolean isDone = false;
    protected float rotation = 0.0F;
    public boolean renderBehind = false;

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < this.startingDuration / 2.0F) {
            this.color.a = this.duration / this.startingDuration / 2.0F;
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
            this.color.a = 0.0F;
        }
    }

    public abstract void render(SpriteBatch paramSpriteBatch);

    public void render(SpriteBatch sb, float x, float y) {
    }

    public abstract void dispose();
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * AbstractGameEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
