package com.megacrit.cardcrawl.unlock;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class AbstractUnlock
        implements Comparable<AbstractUnlock> {
    public String title;
    public AbstractPlayer player = null;
    public String key;
    public UnlockType type;
    public AbstractCard card = null;
    public AbstractRelic relic = null;

    public enum UnlockType {
        CARD, RELIC, LOADOUT, CHARACTER, MISC;
    }

    public void render(SpriteBatch sb) {
    }

    public int compareTo(AbstractUnlock u) {
        switch (this.type) {
            case CARD:
                return this.card.cardID.compareTo(u.card.cardID);
            case RELIC:
                return this.relic.relicId.compareTo(u.relic.relicId);
        }
        return this.title.compareTo(u.title);
    }

    public void onUnlockScreenOpen() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\unlock\
 * AbstractUnlock.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
