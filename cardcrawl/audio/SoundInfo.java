package com.megacrit.cardcrawl.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;

public class SoundInfo {
    public String name;
    public long id;
    public boolean isDone = false;
    private static final float FADE_OUT_DURATION = 5.0F;
    private float fadeDuration = 5.0F;
    public float volumeMultiplier = 1.0F;

    public SoundInfo(String name, long id) {
        this.name = name;
        this.id = id;
    }

    public void update() {
        if (this.fadeDuration != 0.0F) {
            this.fadeDuration -= Gdx.graphics.getDeltaTime();
            this.volumeMultiplier = Interpolation.fade.apply(1.0F, 0.0F, 1.0F - this.fadeDuration / 5.0F);
            if (this.fadeDuration < 0.0F) {
                this.isDone = true;
                this.fadeDuration = 0.0F;
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\audio\SoundInfo.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



