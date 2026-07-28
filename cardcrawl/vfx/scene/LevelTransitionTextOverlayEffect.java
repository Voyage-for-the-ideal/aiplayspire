package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class LevelTransitionTextOverlayEffect
        extends AbstractGameEffect {
    private String name;
    private float alpha = 0.0F;
    private String levelNum;
    private static final float DUR = 5.0F;
    private Color c1 = Settings.GOLD_COLOR.cpy();
    private Color c2 = Settings.BLUE_TEXT_COLOR.cpy();
    private boolean higher = false;

    public LevelTransitionTextOverlayEffect(String name, String levelNum, boolean higher) {
        this.name = name;
        this.levelNum = levelNum;
        this.duration = 5.0F;
        this.startingDuration = 5.0F;
        this.color = Settings.GOLD_COLOR.cpy();
        this.color.a = 0.0F;
        this.higher = higher;
    }

    public LevelTransitionTextOverlayEffect(String name, String levelNum) {
        this.name = name;
        this.levelNum = levelNum;
        this.duration = 5.0F;
        this.startingDuration = 5.0F;
        this.color = Settings.GOLD_COLOR.cpy();
        this.color.a = 0.0F;
    }

    public void update() {
        if (this.duration > 4.0F) {
            this.alpha = Interpolation.pow5Out.apply(1.0F, 0.0F, (this.duration - 4.0F) / 4.0F);
        } else {
            this.alpha = Interpolation.fade.apply(0.0F, 1.0F, this.duration / 2.5F);
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        this.c1.a = this.alpha;
        this.c2.a = this.alpha;
    }

    public void render(SpriteBatch sb) {
        if (this.higher) {
            FontHelper.renderFontCentered(sb, FontHelper.SCP_cardDescFont, this.levelNum, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 290.0F * Settings.scale, this.c2, 1.0F);

            FontHelper.renderFontCentered(sb, FontHelper.dungeonTitleFont, this.name, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 190.0F * Settings.scale, this.c1);

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.SCP_cardDescFont, this.levelNum, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 90.0F * Settings.scale, this.c2, 1.0F);

            FontHelper.renderFontCentered(sb, FontHelper.dungeonTitleFont, this.name, Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F - 10.0F * Settings.scale, this.c1);
        }
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * LevelTransitionTextOverlayEffect.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */
