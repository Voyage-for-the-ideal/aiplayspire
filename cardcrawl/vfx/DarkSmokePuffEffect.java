package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;

public class DarkSmokePuffEffect
        extends AbstractGameEffect {
    private static final float DEFAULT_DURATION = 0.8F;
    private ArrayList<FastDarkSmoke> smoke = new ArrayList<>();

    public DarkSmokePuffEffect(float x, float y) {
        this.duration = 0.8F;
        for (int i = 0; i < 20; i++) {
            this.smoke.add(new FastDarkSmoke(x, y));
        }
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < 0.7F) {
            killSmoke();
        }

        for (FastDarkSmoke b : this.smoke) {
            b.update();
        }
    }

    private void killSmoke() {
        for (FastDarkSmoke s : this.smoke) {
            s.kill();
        }
    }

    public void render(SpriteBatch sb) {
        for (FastDarkSmoke b : this.smoke)
            b.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * DarkSmokePuffEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
