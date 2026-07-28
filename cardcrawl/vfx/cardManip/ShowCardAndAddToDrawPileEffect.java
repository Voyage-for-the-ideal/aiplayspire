package com.megacrit.cardcrawl.vfx.cardManip;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.CardPoofEffect;

public class ShowCardAndAddToDrawPileEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 1.5F;
    private AbstractCard card;
    private static final float PADDING = 30.0F * Settings.scale;

    private boolean randomSpot = false;

    private boolean cardOffset = false;

    public ShowCardAndAddToDrawPileEffect(AbstractCard srcCard, float x, float y, boolean randomSpot,
            boolean cardOffset, boolean toBottom) {
        this.card = srcCard.makeStatEquivalentCopy();
        this.cardOffset = cardOffset;
        this.duration = 1.5F;
        this.randomSpot = randomSpot;

        if (cardOffset) {
            identifySpawnLocation(x, y);
        } else {
            this.card.target_x = x;
            this.card.target_y = y;
        }

        AbstractDungeon.effectsQueue.add(new CardPoofEffect(this.card.target_x, this.card.target_y));
        this.card.drawScale = 0.01F;
        this.card.targetDrawScale = 1.0F;
        if (this.card.type != AbstractCard.CardType.CURSE && this.card.type != AbstractCard.CardType.STATUS
                && AbstractDungeon.player
                        .hasPower("MasterRealityPower")) {
            this.card.upgrade();
        }
        CardCrawlGame.sound.play("CARD_OBTAIN");
        if (toBottom) {
            AbstractDungeon.player.drawPile.addToBottom(this.card);
        } else if (randomSpot) {
            AbstractDungeon.player.drawPile.addToRandomSpot(this.card);
        } else {
            AbstractDungeon.player.drawPile.addToTop(this.card);
        }
    }

    public ShowCardAndAddToDrawPileEffect(AbstractCard srcCard, float x, float y, boolean randomSpot,
            boolean cardOffset) {
        this(srcCard, x, y, randomSpot, cardOffset, false);
    }

    public ShowCardAndAddToDrawPileEffect(AbstractCard srcCard, float x, float y, boolean randomSpot) {
        this(srcCard, x, y, randomSpot, false);
    }

    public ShowCardAndAddToDrawPileEffect(AbstractCard srcCard, boolean randomSpot, boolean toBottom) {
        this.card = srcCard.makeStatEquivalentCopy();
        this.duration = 1.5F;
        this.randomSpot = randomSpot;
        this.card.target_x = MathUtils.random(Settings.WIDTH * 0.1F, Settings.WIDTH * 0.9F);
        this.card.target_y = MathUtils.random(Settings.HEIGHT * 0.8F, Settings.HEIGHT * 0.2F);
        AbstractDungeon.effectsQueue.add(new CardPoofEffect(this.card.target_x, this.card.target_y));
        this.card.drawScale = 0.01F;
        this.card.targetDrawScale = 1.0F;
        if (toBottom) {
            AbstractDungeon.player.drawPile.addToBottom(srcCard);
        } else if (randomSpot) {
            AbstractDungeon.player.drawPile.addToRandomSpot(srcCard);
        } else {
            AbstractDungeon.player.drawPile.addToTop(srcCard);
        }
    }

    private void identifySpawnLocation(float x, float y) {
        int effectCount = 0;
        if (this.cardOffset) {
            effectCount = 1;
        }
        for (AbstractGameEffect e : AbstractDungeon.effectList) {
            if (e instanceof ShowCardAndAddToDrawPileEffect) {
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
        this.duration -= Gdx.graphics.getDeltaTime();
        this.card.update();

        if (this.duration < 0.0F) {
            this.isDone = true;
            this.card.shrink();
            (AbstractDungeon.getCurrRoom()).souls.onToDeck(this.card, this.randomSpot, true);
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
 * ShowCardAndAddToDrawPileEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */
