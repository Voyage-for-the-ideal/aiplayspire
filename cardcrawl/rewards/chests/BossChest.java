package com.megacrit.cardcrawl.rewards.chests;

import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.BlightHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;

import java.util.ArrayList;

public class BossChest
        extends AbstractChest {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("BossChest");
    public static final String[] TEXT = uiStrings.TEXT;
    public ArrayList<AbstractRelic> relics = new ArrayList<>();
    public ArrayList<AbstractBlight> blights = new ArrayList<>();

    public BossChest() {
        this.img = ImageMaster.BOSS_CHEST;
        this.openedImg = ImageMaster.BOSS_CHEST_OPEN;

        this.hb = new Hitbox(256.0F * Settings.scale, 200.0F * Settings.scale);
        this.hb.move(CHEST_LOC_X, CHEST_LOC_Y - 100.0F * Settings.scale);

        if (AbstractDungeon.actNum < 4 || !AbstractPlayer.customMods.contains("Blight Chests")) {

            this.relics.clear();
            for (int i = 0; i < 3; i++) {
                this.relics.add(AbstractDungeon.returnRandomRelic(AbstractRelic.RelicTier.BOSS));
            }
        } else {

            this.blights.clear();
            this.blights.add(BlightHelper.getRandomBlight());
            ArrayList<String> exclusion = new ArrayList<>();
            exclusion.add(((AbstractBlight) this.blights.get(0)).blightID);
            this.blights.add(BlightHelper.getRandomChestBlight(exclusion));
        }
    }

    public void open(boolean bossChest) {
        if (AbstractDungeon.actNum < 4 || !AbstractPlayer.customMods.contains("Blight Chests")) {
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                if (!(r instanceof com.megacrit.cardcrawl.relics.Matryoshka)) {
                    r.onChestOpen(true);
                }
            }

            AbstractDungeon.overlayMenu.proceedButton.setLabel(TEXT[0]);
            CardCrawlGame.sound.play("CHEST_OPEN");
            AbstractDungeon.bossRelicScreen.open(this.relics);
        } else {
            CardCrawlGame.sound.play("CHEST_OPEN");
            AbstractDungeon.bossRelicScreen.openBlight(this.blights);
        }
    }

    public void close() {
        CardCrawlGame.sound.play("CHEST_OPEN");
        this.isOpen = false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rewards\chests\
 * BossChest.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

