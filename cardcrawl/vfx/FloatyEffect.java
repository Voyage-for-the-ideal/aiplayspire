package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

public class FloatyEffect {
    public float x;
    public float y;
    public float vX;
    public float vY;
    private float scale;
    private float minV;

    public FloatyEffect(float distanceScale, float speedScale) {
        this.scale = distanceScale;
        this.speedScale = speedScale;
        this.minV = 0.4F * this.scale;
        this.maxV = 3.0F * this.scale;
        this.threshold = 0.95F * distanceScale;

        this.vX = MathUtils.random(-this.maxV * speedScale, this.maxV * speedScale);
        this.vY = MathUtils.random(-this.maxV * speedScale, this.maxV * speedScale);
    }
    private float maxV;
    private float threshold;
    private static final float SPEED_MIN = 0.4F;
    private static final float SPEED_MAX = 3.0F;
    private static final float EDGE_THRESHOLD = 0.95F;
    private float speedScale;
    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();

        if (this.y > this.threshold) {
            this.vY = -MathUtils.random(this.minV * this.speedScale, this.maxV * this.speedScale);
        } else if (this.y < -this.threshold) {
            this.vY = MathUtils.random(this.minV * this.speedScale, this.maxV * this.speedScale);
        }

        if (this.x > this.threshold) {
            this.vX = -MathUtils.random(this.minV * this.speedScale, this.maxV * this.speedScale);
        } else if (this.x < -this.threshold) {
            this.vX = MathUtils.random(this.minV * this.speedScale, this.maxV * this.speedScale);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\FloatyEffect.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
