package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

import java.util.ArrayList;

public class SwirlyBloodEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private CatmullRomSpline<Vector2> crs = new CatmullRomSpline();
    private ArrayList<Vector2> controlPoints = new ArrayList<>();
    private static final int TRAIL_ACCURACY = 60;
    private Vector2[] points = new Vector2[60];

    private Vector2 pos;
    private Vector2 target;
    private float currentSpeed = 0.0F;
    private static final float VELOCITY_RAMP_RATE = 3000.0F * Settings.scale;

    private float rotation;

    private boolean rotateClockwise = true;
    private boolean stopRotating = false;
    private float rotationRate;

    public SwirlyBloodEffect(float sX, float sY) {
        this.img = ImageMaster.GLOW_SPARK_2;
        this.pos = new Vector2(sX, sY);
        this.target = new Vector2(sX + 100.0F, sY + 100.0F);
        this.crs.controlPoints = (Vector2[]) new Vector2[1];
        this.rotateClockwise = MathUtils.randomBoolean();
        this.rotation = MathUtils.random(0, 359);
        this.controlPoints.clear();
        this.rotationRate = MathUtils.random(800.0F, 1000.0F) * Settings.scale;
        this.currentSpeed = this.rotationRate / 2.0F;
        this.color = new Color(MathUtils.random(0.3F, 1.0F), 0.3F, MathUtils.random(0.6F, 1.0F), 0.25F);
        this.duration = MathUtils.random(1.0F, 1.5F);
        this.renderBehind = MathUtils.randomBoolean();
        this.scale = 1.0E-5F;
    }

    public void update() {
        updateMovement();
    }

    private void updateMovement() {
        Vector2 tmp = new Vector2(this.pos.x - this.target.x, this.pos.y - this.target.y);
        tmp.nor();

        if (!this.stopRotating) {
            if (this.rotateClockwise) {
                this.rotation += Gdx.graphics.getDeltaTime() * this.rotationRate;
            } else {
                this.rotation -= Gdx.graphics.getDeltaTime() * this.rotationRate;
                if (this.rotation < 0.0F) {
                    this.rotation += 360.0F;
                }
            }

            this.rotation %= 360.0F;
        }

        tmp.setAngle(this.rotation);

        tmp.x *= Gdx.graphics.getDeltaTime() * this.currentSpeed;
        tmp.y *= Gdx.graphics.getDeltaTime() * this.currentSpeed;
        this.pos.sub(tmp);

        this.currentSpeed += Gdx.graphics.getDeltaTime() * VELOCITY_RAMP_RATE * 1.5F;

        if (!this.controlPoints.isEmpty()) {
            if (!((Vector2) this.controlPoints.get(0)).equals(this.pos)) {
                this.controlPoints.add(this.pos.cpy());
            }
        } else {
            this.controlPoints.add(this.pos.cpy());
        }

        if (this.controlPoints.size() > 3) {
            Vector2[] vec2Array = new Vector2[0];
            this.crs.set((Vector2[]) this.controlPoints.toArray((Object[]) vec2Array), false);
            for (int i = 0; i < 60; i++) {
                this.points[i] = new Vector2();
                this.crs.valueAt((Vector2) this.points[i], i / 59.0F);
            }
        }

        if (this.controlPoints.size() > 5) {
            this.controlPoints.remove(0);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        this.scale = Interpolation.pow2In.apply(2.0F, 0.01F, this.duration) * Settings.scale;
        if (this.duration < 0.5F) {
            this.color.a = this.duration / 2.0F;
        }
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isDone) {

            sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.color.a / 2.0F));
            float tmpScale = this.scale * 2.0F;
            int i;
            for (i = this.points.length - 1; i > 0; i--) {
                if (this.points[i] != null) {
                    sb.draw((TextureRegion) this.img, (this.points[i]).x - (this.img.packedWidth / 2),
                            (this.points[i]).y - (this.img.packedHeight / 2), this.img.packedWidth / 2.0F,
                            this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, tmpScale,
                            tmpScale, this.rotation);

                    tmpScale *= 0.975F;
                }
            }

            sb.setBlendFunction(770, 1);
            sb.setColor(this.color);
            tmpScale = this.scale * 1.5F;
            for (i = this.points.length - 1; i > 0; i--) {
                if (this.points[i] != null) {
                    sb.draw((TextureRegion) this.img, (this.points[i]).x - (this.img.packedWidth / 2),
                            (this.points[i]).y - (this.img.packedHeight / 2), this.img.packedWidth / 2.0F,
                            this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, tmpScale,
                            tmpScale, this.rotation);

                    tmpScale *= 0.975F;
                }
            }
            sb.setBlendFunction(770, 771);
        }
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * SwirlyBloodEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
