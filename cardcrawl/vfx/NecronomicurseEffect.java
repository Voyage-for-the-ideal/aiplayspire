package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class NecronomicurseEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 1.5F;
    private static final float PADDING = 30.0F * Settings.scale;
    private static final float FAST_DUR = 0.5F;
    private AbstractCard card;
    private float waitTimer = 2.0F;

    public NecronomicurseEffect(AbstractCard card, float x, float y) {
        this.card = card;
        if (Settings.FAST_MODE) {
            this.duration = 0.5F;
        } else {
            this.duration = 1.5F;
        }
        identifySpawnLocation(x, y);
        card.drawScale = 0.01F;
        card.targetDrawScale = 1.0F;
        CardCrawlGame.sound.play("CARD_SELECT");
        AbstractDungeon.player.masterDeck.addToTop(card);
    }

    private void identifySpawnLocation(float x, float y) {
        int effectCount = 0;
        for (AbstractGameEffect e : AbstractDungeon.effectList) {
            if (e instanceof NecronomicurseEffect) {
                effectCount++;
            }
        }

        this.card.current_x = x;
        this.card.current_y = y;
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
        if (this.waitTimer > 0.0F) {
            this.waitTimer -= Gdx.graphics.getDeltaTime();

            return;
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        this.card.current_x += MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        this.card.current_y += MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        this.card.update();

        if (this.duration < 0.0F) {
            this.isDone = true;
            this.card.shrink();

            (AbstractDungeon.getCurrRoom()).souls.obtain(this.card, false);
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isDone && this.waitTimer < 0.0F)
            this.card.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * NecronomicurseEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
