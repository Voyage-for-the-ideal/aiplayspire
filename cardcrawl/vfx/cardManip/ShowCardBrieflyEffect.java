package com.megacrit.cardcrawl.vfx.cardManip;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ShowCardBrieflyEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 2.5F;
    private AbstractCard card;
    private static final float PADDING = 30.0F * Settings.scale;

    public ShowCardBrieflyEffect(AbstractCard card) {
        this.card = card;
        this.duration = 2.5F;
        this.startingDuration = 2.5F;
        identifySpawnLocation(Settings.WIDTH - 96.0F * Settings.scale, Settings.HEIGHT - 32.0F * Settings.scale);
        card.drawScale = 0.01F;
        card.targetDrawScale = 1.0F;
    }

    public ShowCardBrieflyEffect(AbstractCard card, float x, float y) {
        this.card = card;
        this.duration = 2.5F;
        this.startingDuration = 2.5F;
        this.card.drawScale = 0.01F;
        this.card.targetDrawScale = 1.0F;
        this.card.current_x = Settings.WIDTH / 2.0F;
        this.card.current_y = Settings.HEIGHT / 2.0F;
        this.card.target_x = x;
        this.card.target_y = y;
    }

    private void identifySpawnLocation(float x, float y) {
        int effectCount = 0;
        for (AbstractGameEffect e : AbstractDungeon.effectList) {
            if (e instanceof ShowCardBrieflyEffect) {
                effectCount++;
            }
        }

        this.card.current_x = Settings.WIDTH / 2.0F;
        this.card.current_y = Settings.HEIGHT / 2.0F;
        this.card.target_y = Settings.HEIGHT * 0.5F;

        switch (effectCount) {
            case 0:
                this.card.target_x = Settings.WIDTH * 0.5F;
                return;
            case 1:
                this.card.target_x = Settings.WIDTH * 0.5F - PADDING - AbstractCard.IMG_WIDTH;
                return;
            case 2:
                this.card.target_x = Settings.WIDTH * 0.5F + PADDING + AbstractCard.IMG_WIDTH;
                return;
            case 3:
                this.card.target_x = Settings.WIDTH * 0.5F - (PADDING + AbstractCard.IMG_WIDTH) * 2.0F;
                return;
            case 4:
                this.card.target_x = Settings.WIDTH * 0.5F + (PADDING + AbstractCard.IMG_WIDTH) * 2.0F;
                return;
        }
        this.card.target_x = MathUtils.random(Settings.WIDTH * 0.1F, Settings.WIDTH * 0.9F);
        this.card.target_y = MathUtils.random(Settings.HEIGHT * 0.2F, Settings.HEIGHT * 0.8F);
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.6F) {
            this.card.fadingOut = true;
        }
        this.card.update();

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isDone)
            this.card.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\cardManip\
 * ShowCardBrieflyEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
