package com.megacrit.cardcrawl.screens.stats;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class AchievementItem {
    public Hitbox hb = new Hitbox(160.0F * Settings.scale, 160.0F * Settings.scale);
    private TextureAtlas.AtlasRegion img;
    private String title;
    private String desc;
    public String key;
    public boolean isUnlocked;
    private static final Color LOCKED_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.8F);

    public AchievementItem(String title, String desc, String imgUrl, String key, boolean hidden) {
        this.isUnlocked = UnlockTracker.achievementPref.getBoolean(key, false);
        this.key = key;
        if (this.isUnlocked) {
            CardCrawlGame.publisherIntegration.unlockAchievement(key);
            this.title = title;
            this.desc = desc;
            if (StatsScreen.atlas != null) {
                this.img = StatsScreen.atlas.findRegion("unlocked/" + imgUrl);
            }
        } else if (hidden) {
            this.title = AchievementGrid.NAMES[26];
            this.desc = AchievementGrid.TEXT[26];
            if (StatsScreen.atlas != null) {
                this.img = StatsScreen.atlas.findRegion("locked/" + imgUrl);
            }
        } else {
            this.title = title;
            this.desc = desc;
            if (StatsScreen.atlas != null) {
                this.img = StatsScreen.atlas.findRegion("locked/" + imgUrl);
            }
        }
    }

    public AchievementItem(String title, String desc, String imgUrl, String key) {
        this(title, desc, imgUrl, key, false);
    }

    public void update() {
        if (this.hb != null) {
            this.hb.update();
            if (this.hb.hovered) {
                TipHelper.renderGenericTip(InputHelper.mX + 100.0F * Settings.scale, InputHelper.mY, this.title,
                        this.desc);
            }
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
        if (!this.isUnlocked) {
            sb.setColor(LOCKED_COLOR);
        } else {
            sb.setColor(Color.WHITE);
        }

        if (this.hb.hovered) {
            sb.draw((TextureRegion) this.img, x - this.img.packedWidth / 2.0F, y - this.img.packedHeight / 2.0F,
                    this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F, this.img.packedWidth,
                    this.img.packedHeight, Settings.scale * 1.1F, Settings.scale * 1.1F, 0.0F);

        } else {

            sb.draw((TextureRegion) this.img, x - this.img.packedWidth / 2.0F, y - this.img.packedHeight / 2.0F,
                    this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F, this.img.packedWidth,
                    this.img.packedHeight, Settings.scale, Settings.scale, 0.0F);
        }

        this.hb.move(x, y);
        this.hb.render(sb);
    }

    public void reloadImg() {
        this.img = StatsScreen.atlas.findRegion(this.img.name);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\stats\
 * AchievementItem.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

