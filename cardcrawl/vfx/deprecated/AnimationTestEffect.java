package com.megacrit.cardcrawl.vfx.deprecated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.esotericsoftware.spine.*;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class AnimationTestEffect
        extends AbstractGameEffect {
    public float duration;
    public float startingDuration;
    protected Color color;
    protected float scale = 1.0F;
    protected float rotation = 0.0F;

    public boolean renderBehind = false;

    TextureAtlas atlas;
    Skeleton skeleton;
    AnimationState state;

    public AnimationTestEffect() {
        this.duration = 3.0F;
        this.atlas = new TextureAtlas(Gdx.files.internal("animations/skeleton.atlas"));
        SkeletonJson json = new SkeletonJson(this.atlas);
        json.setScale(Settings.scale / 2.0F);
        SkeletonData skeletonData = json.readSkeletonData(Gdx.files.internal("animations/skeleton.json"));
        this.skeleton = new Skeleton(skeletonData);
        this.skeleton.setPosition(1250.0F, 20.0F);
        AnimationStateData stateData = new AnimationStateData(skeletonData);
        this.state = new AnimationState(stateData);
        this.state.setAnimation(0, "animation", true);
    }

    public void update() {
        this.skeleton.setPosition(InputHelper.mX, InputHelper.mY);
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.atlas.dispose();
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
    }

    public void dispose() {
        this.atlas.dispose();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\deprecated\
 * AnimationTestEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
