package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;

public class BobEffect {
    public float y = 0.0F;
    public float speed;
    private float timer = MathUtils.random(0.0F, 359.0F);
    public float dist;
    private static final float DEFAULT_DIST = 5.0F * Settings.scale;
    private static final float DEFAULT_SPEED = 4.0F;

    public BobEffect() {
        this(DEFAULT_DIST, 4.0F);
    }

    public BobEffect(float speed) {
        this(DEFAULT_DIST, speed);
    }

    public BobEffect(float dist, float speed) {
        this.speed = speed;
        this.dist = dist;
    }

    public void update() {
        this.y = MathUtils.sin(this.timer) * this.dist;
        this.timer += Gdx.graphics.getDeltaTime() * this.speed;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\BobEffect.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
