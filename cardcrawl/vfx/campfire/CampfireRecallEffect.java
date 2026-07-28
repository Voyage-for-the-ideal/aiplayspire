package com.megacrit.cardcrawl.vfx.campfire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.ObtainKeyEffect;

public class CampfireRecallEffect
        extends AbstractGameEffect {
    private static final float DUR = 2.0F;
    private boolean hasRecalled = false;
    private Color screenColor = AbstractDungeon.fadeColor.cpy();

    public CampfireRecallEffect() {
        this.duration = 2.0F;
        this.screenColor.a = 0.0F;
        ((RestRoom) AbstractDungeon.getCurrRoom()).cutFireSound();
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        updateBlackScreenColor();

        if (this.duration < 1.0F && !this.hasRecalled) {
            this.hasRecalled = true;
            CardCrawlGame.sound.play("ATTACK_MAGIC_SLOW_2");

            (AbstractDungeon.getCurrRoom()).rewards.clear();
            AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.RED));
            (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
            CardCrawlGame.metricData.addCampfireChoiceData("RECALL");
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
            ((RestRoom) AbstractDungeon.getCurrRoom()).fadeIn();
            (AbstractDungeon.getCurrRoom()).phase = AbstractRoom.RoomPhase.COMPLETE;
        }
    }

    private void updateBlackScreenColor() {
        if (this.duration > 1.5F) {
            this.screenColor.a = Interpolation.fade.apply(1.0F, 0.0F, (this.duration - 1.5F) * 2.0F);

        } else if (this.duration < 1.0F) {
            this.screenColor.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration);
        } else {
            this.screenColor.a = 1.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.screenColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\campfire\
 * CampfireRecallEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
