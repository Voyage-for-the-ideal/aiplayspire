package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.DialogWord;

import java.util.ArrayList;

public class ThoughtBubble
        extends AbstractGameEffect {
    private static final float DEFAULT_DURATION = 2.0F;
    private static final float OFFSET_X = 190.0F * Settings.scale, OFFSET_Y = 124.0F * Settings.scale;
    private static final float CLOUD_W = 100.0F * Settings.scale;
    private static final float CLOUD_H = 50.0F * Settings.scale;
    private ArrayList<CloudBubble> bubbles;

    public ThoughtBubble(float x, float y, String msg, boolean isPlayer) {
        this(x, y, 2.0F, msg, isPlayer);
    }
    public ThoughtBubble(float x, float y, float duration, String msg, boolean isPlayer) {
        float off_x;
        this.bubbles = new ArrayList<>();
        if (msg == null) {
            this.isDone = true;

            return;
        }
        for (AbstractGameEffect e : AbstractDungeon.effectList) {
            if (e instanceof ThoughtBubble && !e.equals(this)) {
                ((ThoughtBubble) e).killClouds();
            }
        }

        this.duration = duration;

        y += OFFSET_Y;
        if (isPlayer) {
            x += OFFSET_X;
        } else {
            x -= OFFSET_X;
        }

        AbstractDungeon.effectsQueue.add(new SpeechTextEffect(x, y, duration, msg, DialogWord.AppearEffect.BUMP_IN));

        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(0.7F, 1.1F), y
                        + CLOUD_H *
                                MathUtils.random(0.1F, 0.3F),
                MathUtils.random(1.0F, 1.2F)));
        this.bubbles.add(new CloudBubble(x - CLOUD_W *

                MathUtils.random(0.7F, 1.1F), y
                        + CLOUD_H *
                                MathUtils.random(0.1F, 0.3F),
                MathUtils.random(1.0F, 1.2F)));
        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(0.7F, 1.1F), y
                        + CLOUD_H *
                                MathUtils.random(-0.1F, -0.3F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x - CLOUD_W *

                MathUtils.random(0.7F, 1.1F), y
                        + CLOUD_H *
                                MathUtils.random(-0.1F, -0.3F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(-0.2F, 0.2F), y
                        + CLOUD_H *
                                MathUtils.random(0.65F, 0.72F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(-0.2F, 0.2F), y
                        - CLOUD_H *
                                MathUtils.random(0.65F, 0.72F),
                MathUtils.random(1.0F, 1.2F)));

        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(0.3F, 0.8F), y
                        + CLOUD_H *
                                MathUtils.random(0.3F, 0.7F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x - CLOUD_W *

                MathUtils.random(0.3F, 0.8F), y
                        + CLOUD_H *
                                MathUtils.random(0.3F, 0.7F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x + CLOUD_W *

                MathUtils.random(0.3F, 0.8F), y
                        - CLOUD_H *
                                MathUtils.random(0.3F, 0.7F),
                MathUtils.random(0.9F, 1.1F)));
        this.bubbles.add(new CloudBubble(x - CLOUD_W *

                MathUtils.random(0.3F, 0.8F), y
                        - CLOUD_H *
                                MathUtils.random(0.3F, 0.7F),
                MathUtils.random(0.9F, 1.1F)));

        if (isPlayer) {
            off_x = OFFSET_X;
        } else {
            off_x = -OFFSET_X;
        }
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(-0.05F, -0.15F), y
                        - OFFSET_Y *
                                MathUtils.random(0.67F, 0.72F),
                MathUtils.random(0.4F, 0.45F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.07F, 0.15F), y
                        - OFFSET_Y *
                                MathUtils.random(0.65F, 0.7F),
                MathUtils.random(0.4F, 0.45F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.1F, 0.2F), y
                        - OFFSET_Y *
                                MathUtils.random(0.9F, 1.02F),
                MathUtils.random(0.35F, 0.4F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.3F, 0.35F), y
                        - OFFSET_Y *
                                MathUtils.random(1.05F, 1.1F),
                MathUtils.random(0.18F, 0.23F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.35F, 0.45F), y
                        - OFFSET_Y *
                                MathUtils.random(1.1F, 1.2F),
                MathUtils.random(0.1F, 0.13F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.45F, 0.5F), y
                        - OFFSET_Y *
                                MathUtils.random(1.1F, 1.16F),
                MathUtils.random(0.08F, 0.09F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.5F, 0.6F), y
                        - OFFSET_Y *
                                MathUtils.random(1.1F, 1.16F),
                MathUtils.random(0.08F, 0.09F)));
        this.bubbles.add(new CloudBubble(x - off_x *

                MathUtils.random(0.6F, 0.65F), y
                        - OFFSET_Y *
                                MathUtils.random(1.05F, 1.12F),
                MathUtils.random(0.08F, 0.09F)));
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < 0.3F) {
            killClouds();
        }

        for (CloudBubble b : this.bubbles) {
            b.update();
        }
    }

    private void killClouds() {
        for (CloudBubble b : this.bubbles) {
            b.kill();
        }
    }

    public void render(SpriteBatch sb) {
        for (CloudBubble b : this.bubbles)
            b.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\ThoughtBubble.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
