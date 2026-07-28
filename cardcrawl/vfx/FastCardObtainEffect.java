package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Omamori;

public class FastCardObtainEffect
        extends AbstractGameEffect {
    private AbstractCard card;

    public FastCardObtainEffect(AbstractCard card, float x, float y) {
        if (card.color == AbstractCard.CardColor.CURSE && AbstractDungeon.player.hasRelic("Omamori") &&
                (AbstractDungeon.player.getRelic("Omamori")).counter != 0) {
            ((Omamori) AbstractDungeon.player.getRelic("Omamori")).use();
            this.duration = 0.0F;
            this.isDone = true;
        }

        CardHelper.obtain(card.cardID, card.rarity, card.color);

        this.card = card;
        this.duration = 0.01F;
        card.current_x = x;
        card.current_y = y;
        CardCrawlGame.sound.play("CARD_SELECT");
    }

    public void update() {
        if (this.isDone) {
            return;
        }

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
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * FastCardObtainEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
