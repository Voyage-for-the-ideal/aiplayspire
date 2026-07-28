package com.megacrit.cardcrawl.rooms;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MonsterRoomBoss
        extends MonsterRoom {
    private static final Logger logger = LogManager.getLogger(MonsterRoomBoss.class.getName());

    public void onPlayerEntry() {
        this.monsters = CardCrawlGame.dungeon.getBoss();
        logger.info("BOSSES: " + AbstractDungeon.bossList.size());
        CardCrawlGame.metricData.path_taken.add("BOSS");
        CardCrawlGame.music.silenceBGM();
        AbstractDungeon.bossList.remove(0);

        if (this.monsters != null) {
            this.monsters.init();
        }

        waitTimer = 0.1F;
    }

    public AbstractCard.CardRarity getCardRarity(int roll) {
        return AbstractCard.CardRarity.RARE;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rooms\
 * MonsterRoomBoss.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

