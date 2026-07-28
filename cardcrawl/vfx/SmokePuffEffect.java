package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;

public class SmokePuffEffect
        extends AbstractGameEffect {
    private static final float DEFAULT_DURATION = 0.8F;
    private ArrayList<FastSmoke> smoke = new ArrayList<>();

    public SmokePuffEffect(float x, float y) {
        this.duration = 0.8F;
        for (int i = 0; i < 30; i++) {
            this.smoke.add(new FastSmoke(x, y));
        }
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < 0.7F) {
            killSmoke();
        }

        for (FastSmoke b : this.smoke) {
            b.update();
        }
    }

    private void killSmoke() {
        for (FastSmoke s : this.smoke) {
            s.kill();
        }
    }

    public void render(SpriteBatch sb) {
        for (FastSmoke b : this.smoke)
            b.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * SmokePuffEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
