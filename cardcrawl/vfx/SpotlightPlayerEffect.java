package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class SpotlightPlayerEffect
        extends AbstractGameEffect {
    public void update() {
        if (this.duration == 3.0F) {
            CardCrawlGame.sound.playA("INTIMIDATE", -0.6F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > 1.5F) {
            this.color.a = Interpolation.pow5In.apply(0.5F, 0.0F, (this.duration - 1.5F) / 1.5F);
        } else {
            this.color.a = Interpolation.exp10In.apply(0.0F, 0.5F, this.duration / 1.5F);
        }

        if (this.duration < 0.0F) {
            this.color.a = 0.0F;
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(ImageMaster.SPOTLIGHT_VFX, 0.0F, 0.0F,
                AbstractDungeon.player.drawX + AbstractDungeon.player.hb_w * 2.0F, Settings.HEIGHT);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * SpotlightPlayerEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
