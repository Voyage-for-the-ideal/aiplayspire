package com.megacrit.cardcrawl.screens.runHistory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.daily.mods.AbstractDailyMod;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.screens.stats.RunData;

import java.util.ArrayList;

public class ModIcons {
    private static final float DAILY_MOD_ICON_SIZE = 52.0F * Settings.scale;
    private static final float DAILY_MOD_VISIBLE_SIZE = DAILY_MOD_ICON_SIZE * 0.75F;

    private static final float HOVER_SCALE = 1.5F;
    private ArrayList<AbstractDailyMod> dailyModList = new ArrayList<>();
    private ArrayList<Hitbox> hitboxes = new ArrayList<>();

    public void setRunData(RunData runData) {
        this.dailyModList.clear();
        this.hitboxes.clear();
        if (runData.daily_mods != null) {
            for (String modId : runData.daily_mods) {
                this.dailyModList.add(ModHelper.getMod(modId));
                this.hitboxes.add(new Hitbox(DAILY_MOD_VISIBLE_SIZE, DAILY_MOD_VISIBLE_SIZE));
            }
        }
    }

    public boolean hasMods() {
        return (this.dailyModList.size() > 0);
    }

    public void update() {
        boolean isHovered = false;
        for (int i = 0; i < this.hitboxes.size(); i++) {
            AbstractDailyMod mod = this.dailyModList.get(i);
            Hitbox hbox = this.hitboxes.get(i);
            hbox.update();
            if (hbox.hovered) {
                isHovered = true;
                TipHelper.renderGenericTip(hbox.x + 64.0F * Settings.scale, hbox.y + DAILY_MOD_VISIBLE_SIZE / 2.0F,
                        mod.name, mod.description);
            }
        }

        if (isHovered) {
            CardCrawlGame.cursor.changeType(GameCursor.CursorType.INSPECT);
        }
    }

    public void renderDailyMods(SpriteBatch sb, float x, float y) {
        float drawX = x;
        float drawY = y - DAILY_MOD_VISIBLE_SIZE;

        for (int i = 0; i < this.dailyModList.size(); i++) {
            AbstractDailyMod mod = this.dailyModList.get(i);
            Hitbox hbox = this.hitboxes.get(i);

            float halfSize = DAILY_MOD_ICON_SIZE / 2.0F;
            float cx = drawX + halfSize;
            float cy = drawY + halfSize;
            hbox.move(cx, cy);
            hbox.render(sb);

            if (mod != null && mod.img != null) {
                float drawSize = DAILY_MOD_ICON_SIZE;
                float offset = 0.0F;

                if (hbox.hovered) {
                    offset = drawSize * 0.25F;
                    drawSize *= 1.5F;
                }
                sb.draw(mod.img, hbox.x - offset, hbox.y - offset, drawSize, drawSize);
            }
            drawX += DAILY_MOD_VISIBLE_SIZE;
        }
    }

    public float approximateWidth() {
        return this.dailyModList.size() * DAILY_MOD_VISIBLE_SIZE;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\runHistory
 * \ModIcons.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

