package com.megacrit.cardcrawl.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.MathHelper;

public class DialogWord {
    private BitmapFont font;
    private WordEffect effect;
    private WordColor wColor;
    public String word;
    public int line = 0;
    private float x;
    private float y;
    private float target_x;
    private float timer = 0.0F;
    private float target_y;
    private float offset_x;
    private float offset_y;
    private Color color;
    private Color targetColor;
    private float scale = 1.0F;
    private float targetScale = 1.0F;
    private static final float BUMP_OFFSET = 20.0F * Settings.scale;
    private static GlyphLayout gl;
    private static final float COLOR_LERP_SPEED = 8.0F;
    private static final float SHAKE_AMT = 2.0F * Settings.scale;
    private static final float DIALOG_FADE_Y = 50.0F * Settings.scale;

    private static final float WAVY_DIST = 3.0F;
    private static final float SHAKE_INTERVAL = 0.02F;

    public enum AppearEffect {
        NONE, FADE_IN, GROW_IN, BUMP_IN;
    }

    public enum WordEffect {
        NONE, WAVY, SLOW_WAVY, SHAKY, PULSE;
    }

    public enum WordColor {
        DEFAULT, RED, GREEN, BLUE, GOLD, PURPLE, WHITE;
    }

    public DialogWord(BitmapFont font, String word, AppearEffect a_effect, WordEffect effect, WordColor wColor, float x,
            float y, int line) {
        if (gl == null) {
            gl = new GlyphLayout();
        }

        this.font = font;
        this.effect = effect;
        this.wColor = wColor;
        this.word = word;
        this.x = x;
        this.y = y;
        this.target_x = x;
        this.target_y = y;
        this.targetColor = getColor();
        this.line = line;
        this.color = new Color(this.targetColor.r, this.targetColor.g, this.targetColor.b, 0.0F);

        if (effect == WordEffect.WAVY || effect == WordEffect.SLOW_WAVY) {
            this.timer = MathUtils.random(1.5707964F);
        }

        switch (a_effect) {
            case FADE_IN:
            default:
                return;
            case GROW_IN:
                this.y -= BUMP_OFFSET;
                this.scale = 0.0f;
                return;
            case BUMP_IN:
                this.y -= BUMP_OFFSET;
                return;
        }

    }

    private Color getColor() {
        switch (this.wColor) {
            case RED:
                return Settings.RED_TEXT_COLOR.cpy();
            case GREEN:
                return Settings.GREEN_TEXT_COLOR.cpy();
            case BLUE:
                return Settings.BLUE_TEXT_COLOR.cpy();
            case GOLD:
                return Settings.GOLD_COLOR.cpy();
            case PURPLE:
                return Settings.PURPLE_COLOR.cpy();
            case WHITE:
                return Settings.CREAM_COLOR.cpy();
            default:
                return Settings.CREAM_COLOR.cpy();
        }

    }

    public void update() {
        if (this.x != this.target_x) {
            this.x = MathUtils.lerp(this.x, this.target_x, Gdx.graphics.getDeltaTime() * 12.0F);
        }
        if (this.y != this.target_y) {
            this.y = MathUtils.lerp(this.y, this.target_y, Gdx.graphics.getDeltaTime() * 12.0F);
        }

        this.color = this.color.lerp(this.targetColor, Gdx.graphics.getDeltaTime() * 8.0F);

        if (this.scale != this.targetScale) {
            this.scale = MathHelper.scaleLerpSnap(this.scale, this.targetScale);
        }

        applyEffects();
    }

    private void applyEffects() {
        switch (this.effect) {
            case SHAKY:
                this.timer -= Gdx.graphics.getDeltaTime();
                if (this.timer < 0.0F) {
                    this.offset_x = MathUtils.random(-SHAKE_AMT, SHAKE_AMT);
                    this.offset_y = MathUtils.random(-SHAKE_AMT, SHAKE_AMT);
                    this.timer = 0.02F;
                }
                break;
            case WAVY:
                this.timer += Gdx.graphics.getDeltaTime() * 6.0F;
                this.offset_y = (float) Math.cos(this.timer) * Settings.scale * 3.0F;
                break;
            case SLOW_WAVY:
                this.timer += Gdx.graphics.getDeltaTime() * 3.0F;
                this.offset_y = (float) Math.cos(this.timer) * Settings.scale * 1.5F;
                break;
        }
    }

    public void dialogFadeOut() {
        this.targetColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
        this.target_y -= DIALOG_FADE_Y;
    }

    public void shiftY(float shiftAmount) {
        this.target_y += shiftAmount;
    }

    public void shiftX(float shiftAmount) {
        this.target_x += shiftAmount;
    }

    public void setX(float newX) {
        this.target_x = newX;
    }

    public void render(SpriteBatch sb) {
        this.font.setColor(this.color);
        this.font.getData().setScale(this.scale);
        this.font.draw((Batch) sb, this.word, this.x + this.offset_x, this.y + this.offset_y);
        this.font.getData().setScale(1.0F);
    }

    public void render(SpriteBatch sb, float y2) {
        this.font.setColor(this.color);
        this.font.getData().setScale(this.scale);
        this.font.draw((Batch) sb, this.word, this.x + this.offset_x, this.y + this.offset_y + y2);
        this.font.getData().setScale(1.0F);
    }

    public static WordEffect identifyWordEffect(String word) {
        if (word.length() > 2) {
            if (word.charAt(0) == '@' && word.charAt(word.length() - 1) == '@')
                return WordEffect.SHAKY;
            if (word.charAt(0) == '~' && word.charAt(word.length() - 1) == '~') {
                return WordEffect.WAVY;
            }
        }

        return WordEffect.NONE;
    }

    public static WordColor identifyWordColor(String word) {
        if (word.charAt(0) == '#') {
            switch (word.charAt(1)) {
                case 'r':
                    return WordColor.RED;
                case 'g':
                    return WordColor.GREEN;
                case 'b':
                    return WordColor.BLUE;
                case 'y':
                    return WordColor.GOLD;
                case 'p':
                    return WordColor.PURPLE;
            }

        }
        return WordColor.DEFAULT;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\DialogWord.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

