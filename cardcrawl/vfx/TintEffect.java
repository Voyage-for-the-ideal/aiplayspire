package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;

public class TintEffect {
    public Color color = new Color(1.0F, 1.0F, 1.0F, 1.0F);
    private Color targetColor = new Color(1.0F, 1.0F, 1.0F, 1.0F);
    private float lerpSpeed = 3.0F;

    public void changeColor(Color targetColor, float lerpSpeed) {
        this.targetColor = targetColor;
        this.lerpSpeed = lerpSpeed;
    }

    public void changeColor(Color targetColor) {
        changeColor(targetColor, 3.0F);
    }

    public void fadeOut() {
        this.targetColor.set(this.color.r, this.color.g, this.color.b, 0.0F);
        this.lerpSpeed = 3.0F;
    }

    public void update() {
        this.color.lerp(this.targetColor, Gdx.graphics.getDeltaTime() * this.lerpSpeed);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\TintEffect.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
