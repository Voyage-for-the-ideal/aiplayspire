package com.megacrit.cardcrawl.vfx.cardManip;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Omamori;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.CardPoofEffect;

public class ShowCardAndObtainEffect extends AbstractGameEffect {
    private static final float EFFECT_DUR = 2.0F;
    private static final float FAST_DUR = 0.5F;
    private AbstractCard card;
    private static final float PADDING = 30.0F * Settings.scale;
    private boolean converge;

    public ShowCardAndObtainEffect(AbstractCard card, float x, float y, boolean convergeCards) {
        if (card.color == AbstractCard.CardColor.CURSE && AbstractDungeon.player.hasRelic("Omamori") &&
                (AbstractDungeon.player.getRelic("Omamori")).counter != 0) {
            ((Omamori) AbstractDungeon.player.getRelic("Omamori")).use();
            this.duration = 0.0F;
            this.isDone = true;
            this.converge = convergeCards;
        }

        UnlockTracker.markCardAsSeen(card.cardID);

        CardHelper.obtain(card.cardID, card.rarity, card.color);

        this.card = card;
        if (Settings.FAST_MODE) {
            this.duration = 0.5F;
        } else {
            this.duration = 2.0F;
        }
        identifySpawnLocation(x, y);
        AbstractDungeon.effectsQueue.add(new CardPoofEffect(card.target_x, card.target_y));
        card.drawScale = 0.01F;
        card.targetDrawScale = 1.0F;
    }

    public ShowCardAndObtainEffect(AbstractCard card, float x, float y) {
        this(card, x, y, true);
    }

    private void identifySpawnLocation(float x, float y) {
        int effectCount = 0;
        for (AbstractGameEffect e : AbstractDungeon.effectList) {
            if (e instanceof ShowCardAndObtainEffect) {
                effectCount++;
            }
        }

        this.card.current_x = x;
        this.card.current_y = y;

        if (this.converge) {
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
        } else {

            this.card.target_x = this.card.current_x;
            this.card.target_y = this.card.current_y;
        }
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.card.update();

        if (this.duration < 0.0F) {

            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onObtainCard(this.card);
            }

            this.isDone = true;
            this.card.shrink();
            (AbstractDungeon.getCurrRoom()).souls.obtain(this.card, true);
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onMasterDeckChange();
            }
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
 * ShowCardAndObtainEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */
