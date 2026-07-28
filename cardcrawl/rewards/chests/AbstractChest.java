package com.megacrit.cardcrawl.rewards.chests;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractChest {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("AbstractChest");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final Logger logger = LogManager.getLogger(AbstractChest.class.getName());
    public static final float CHEST_LOC_X = Settings.WIDTH / 2.0F + 348.0F * Settings.scale;
    public static final float CHEST_LOC_Y = AbstractDungeon.floorY + 192.0F * Settings.scale;

    private static final int RAW_W = 512;

    protected Hitbox hb;

    protected Texture img;

    protected Texture openedImg;

    public boolean isOpen = false;

    public int COMMON_CHANCE;

    public int UNCOMMON_CHANCE;

    public int RARE_CHANCE;

    public int GOLD_CHANCE;

    public int GOLD_AMT;
    public RelicReward relicReward;
    public boolean goldReward = false;
    public boolean cursed = false;

    protected boolean keyRequirement() {
        this.isOpen = true;
        return true;
    }

    public void randomizeReward() {
        int roll = AbstractDungeon.treasureRng.random(0, 99);

        if (roll < this.GOLD_CHANCE) {
            this.goldReward = true;
        }

        if (roll < this.COMMON_CHANCE) {
            this.relicReward = RelicReward.COMMON_RELIC;
        } else if (roll < this.UNCOMMON_CHANCE + this.COMMON_CHANCE) {
            this.relicReward = RelicReward.UNCOMMON_RELIC;
        } else {
            this.relicReward = RelicReward.RARE_RELIC;
        }
    }

    public void open(boolean bossChest) {
        AbstractDungeon.overlayMenu.proceedButton.setLabel(TEXT[0]);
        for (AbstractRelic r : AbstractDungeon.player.relics) {
            r.onChestOpen(bossChest);
        }
        CardCrawlGame.sound.play("CHEST_OPEN");

        if (this.goldReward) {
            if (Settings.isDailyRun) {
                AbstractDungeon.getCurrRoom().addGoldToRewards(this.GOLD_AMT);
            } else {
                AbstractDungeon.getCurrRoom().addGoldToRewards(
                        Math.round(AbstractDungeon.treasureRng.random(this.GOLD_AMT * 0.9F, this.GOLD_AMT * 1.1F)));
            }
        }

        if (this.cursed) {
            AbstractDungeon.topLevelEffects.add(new ShowCardAndObtainEffect(
                    AbstractDungeon.returnRandomCurse(), this.hb.cX, this.hb.cY));
        }

        switch (this.relicReward) {
            case COMMON_RELIC:
                AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.COMMON);
                break;
            case UNCOMMON_RELIC:
                AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.UNCOMMON);
                break;
            case RARE_RELIC:
                AbstractDungeon.getCurrRoom().addRelicToRewards(AbstractRelic.RelicTier.RARE);
                break;
            default:
                logger.info("ERROR: Unspecified reward: " + this.relicReward.name());
                break;
        }

        if (Settings.isFinalActAvailable && !Settings.hasSapphireKey) {
            AbstractDungeon.getCurrRoom().addSapphireKey(
                    (AbstractDungeon.getCurrRoom()).rewards.get((AbstractDungeon.getCurrRoom()).rewards.size() - 1));
        }

        for (AbstractRelic r : AbstractDungeon.player.relics) {
            r.onChestOpenAfter(bossChest);
        }

        AbstractDungeon.combatRewardScreen.open();
    }

    public enum RelicReward {
        COMMON_RELIC, UNCOMMON_RELIC, RARE_RELIC;
    }

    public void update() {
        this.hb.update();
        if (((this.hb.hovered && InputHelper.justClickedLeft) || CInputActionSet.select.isJustPressed())
                && !AbstractDungeon.isScreenUp && !this.isOpen &&
                keyRequirement()) {
            InputHelper.justClickedLeft = false;
            open(false);
        }
    }

    public void close() {
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        float angle = 0.0F;

        if (this.isOpen && this.openedImg == null) {
            angle = 180.0F;
        }

        float xxx = Settings.WIDTH / 2.0F + 348.0F * Settings.scale;

        if (!this.isOpen || angle == 180.0F) {
            sb.draw(this.img, CHEST_LOC_X - 256.0F, CHEST_LOC_Y - 256.0F, 256.0F, 256.0F, 512.0F, 512.0F,
                    Settings.scale, Settings.scale, angle, 0, 0, 512, 512, false, false);

            if (this.hb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.3F));
                sb.draw(this.img, CHEST_LOC_X - 256.0F, CHEST_LOC_Y - 256.0F, 256.0F, 256.0F, 512.0F, 512.0F,
                        Settings.scale, Settings.scale, angle, 0, 0, 512, 512, false, false);

                sb.setBlendFunction(770, 771);
            }
        } else {
            sb.draw(this.openedImg, xxx - 256.0F, CHEST_LOC_Y - 256.0F, 256.0F, 256.0F, 512.0F, 512.0F, Settings.scale,
                    Settings.scale, angle, 0, 0, 512, 512, false, false);
        }

        if (Settings.isControllerMode && !this.isOpen) {
            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.select
                    .getKeyImg(), CHEST_LOC_X - 32.0F - 150.0F * Settings.scale,
                    CHEST_LOC_Y - 32.0F - 210.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
        }

        this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rewards\chests\
 * AbstractChest.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

