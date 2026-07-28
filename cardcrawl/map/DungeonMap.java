package com.megacrit.cardcrawl.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;

public class DungeonMap {
    private static Texture top;
    private static Texture mid;
    private static Texture bot;
    public float targetAlpha = 0.0F;
    private static Texture blend;
    public static Texture boss;
    public static Texture bossOutline;
    private static final Color NOT_TAKEN_COLOR = new Color(0.34F, 0.34F, 0.34F, 1.0F);
    private Color bossNodeColor = NOT_TAKEN_COLOR.cpy();
    private Color baseMapColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private float mapMidDist;
    private static float mapOffsetY;
    private static final float BOSS_W = Settings.isMobile ? (560.0F * Settings.scale) : (512.0F * Settings.scale);
    private static final float BOSS_OFFSET_Y = 1416.0F * Settings.scale;
    private static final float H = 1020.0F * Settings.scale;
    private static final float BLEND_H = 512.0F * Settings.scale;

    public Hitbox bossHb;

    public boolean atBoss = false;
    private Color reticleColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);

    public Legend legend = new Legend();

    public DungeonMap() {
        if (top == null) {
            top = ImageMaster.loadImage("images/ui/map/mapTop.png");
            mid = ImageMaster.loadImage("images/ui/map/mapMid.png");
            bot = ImageMaster.loadImage("images/ui/map/mapBot.png");
            blend = ImageMaster.loadImage("images/ui/map/mapBlend.png");
        }
        this.bossHb = new Hitbox(400.0F * Settings.scale, 360.0F * Settings.scale);
    }

    public void update() {
        this.legend.update(this.baseMapColor.a, (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP));
        this.baseMapColor.a = MathHelper.fadeLerpSnap(this.baseMapColor.a, this.targetAlpha);

        this.bossHb.move(Settings.WIDTH / 2.0F, DungeonMapScreen.offsetY + mapOffsetY + BOSS_OFFSET_Y + BOSS_W / 2.0F);
        this.bossHb.update();
        updateReticle();

        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMPLETE
                && AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP && (Settings.isDebug ||
                        (AbstractDungeon.getCurrMapNode()).y == 14 || (AbstractDungeon.id
                                .equals("TheEnding") && (AbstractDungeon.getCurrMapNode()).y == 2))) {
            if (this.bossHb.hovered && (InputHelper.justClickedLeft || CInputActionSet.select.isJustPressed())) {

                (AbstractDungeon.getCurrMapNode()).taken = true;
                MapRoomNode node2 = AbstractDungeon.getCurrMapNode();
                for (MapEdge e : node2.getEdges()) {
                    if (e != null) {
                        e.markAsTaken();
                    }
                }

                InputHelper.justClickedLeft = false;
                CardCrawlGame.music.fadeOutTempBGM();
                MapRoomNode node = new MapRoomNode(-1, 15);
                node.room = (AbstractRoom) new MonsterRoomBoss();
                AbstractDungeon.nextRoom = node;

                if (AbstractDungeon.pathY.size() > 1) {
                    AbstractDungeon.pathX.add(AbstractDungeon.pathX.get(AbstractDungeon.pathX.size() - 1));
                    AbstractDungeon.pathY.add(Integer.valueOf(
                            ((Integer) AbstractDungeon.pathY.get(AbstractDungeon.pathY.size() - 1)).intValue() + 1));
                } else {
                    AbstractDungeon.pathX.add(Integer.valueOf(1));
                    AbstractDungeon.pathY.add(Integer.valueOf(15));
                }

                AbstractDungeon.nextRoomTransitionStart();
                this.bossHb.hovered = false;
            }
        }

        if (this.bossHb.hovered || this.atBoss) {
            this.bossNodeColor = MapRoomNode.AVAILABLE_COLOR.cpy();
        } else {
            this.bossNodeColor.lerp(NOT_TAKEN_COLOR, Gdx.graphics.getDeltaTime() * 8.0F);
        }

        this.bossNodeColor.a = this.baseMapColor.a;
    }

    private void updateReticle() {
        if (!Settings.isControllerMode) {
            return;
        }

        if (this.bossHb.hovered) {
            this.reticleColor.a += Gdx.graphics.getDeltaTime() * 3.0F;

            if (this.reticleColor.a > 1.0F) {
                this.reticleColor.a = 1.0F;
            }
        } else {
            this.reticleColor.a = 0.0F;
        }
    }

    private float calculateMapSize() {
        if (AbstractDungeon.id.equals("TheEnding")) {
            return Settings.MAP_DST_Y * 4.0F - 1380.0F * Settings.scale;
        }
        return Settings.MAP_DST_Y * 16.0F - 1380.0F * Settings.scale;
    }

    public void show() {
        this.targetAlpha = 1.0F;
        this.mapMidDist = calculateMapSize();
        mapOffsetY = this.mapMidDist - 120.0F * Settings.scale;
    }

    public void hide() {
        this.targetAlpha = 0.0F;
    }

    public void hideInstantly() {
        this.targetAlpha = 0.0F;
        this.baseMapColor.a = 0.0F;
        this.legend.c.a = 0.0F;
    }

    public void render(SpriteBatch sb) {
        if (!AbstractDungeon.id.equals("TheEnding")) {
            renderNormalMap(sb);
        } else {
            renderFinalActMap(sb);
        }
    }

    private void renderNormalMap(SpriteBatch sb) {
        sb.setColor(this.baseMapColor);

        if (!Settings.isMobile) {
            sb.draw(top, 0.0F, H + DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH, 1080.0F * Settings.scale);
        } else {
            sb.draw(top, -Settings.WIDTH * 0.05F, H + DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH * 1.1F,
                    1080.0F * Settings.scale);
        }

        renderMapCenters(sb);

        if (!Settings.isMobile) {
            sb.draw(bot, 0.0F, -this.mapMidDist + DungeonMapScreen.offsetY + mapOffsetY + 1.0F, Settings.WIDTH,
                    1080.0F * Settings.scale);

        } else {

            sb.draw(bot, -Settings.WIDTH * 0.05F, -this.mapMidDist + DungeonMapScreen.offsetY + mapOffsetY + 1.0F,
                    Settings.WIDTH * 1.1F, 1080.0F * Settings.scale);
        }

        renderMapBlender(sb);
        this.legend.render(sb);
    }

    private void renderFinalActMap(SpriteBatch sb) {
        sb.setColor(this.baseMapColor);

        if (!Settings.isMobile) {
            sb.draw(top, 0.0F, H + DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH, 1080.0F * Settings.scale);
            sb.draw(bot, 0.0F, -this.mapMidDist + DungeonMapScreen.offsetY + mapOffsetY + 1.0F, Settings.WIDTH,
                    1080.0F * Settings.scale);

        } else {

            sb.draw(top, -Settings.WIDTH * 0.05F, H + DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH * 1.1F,
                    1080.0F * Settings.scale);

            sb.draw(bot, -Settings.WIDTH * 0.05F, -this.mapMidDist + DungeonMapScreen.offsetY + mapOffsetY + 1.0F,
                    Settings.WIDTH * 1.1F, 1080.0F * Settings.scale);
        }

        renderMapBlender(sb);
        this.legend.render(sb);
    }

    public void renderBossIcon(SpriteBatch sb) {
        if (boss != null) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.bossNodeColor.a));
            if (!Settings.isMobile) {
                sb.draw(bossOutline, Settings.WIDTH / 2.0F - BOSS_W / 2.0F,
                        DungeonMapScreen.offsetY + mapOffsetY + BOSS_OFFSET_Y, BOSS_W, BOSS_W);

                sb.setColor(this.bossNodeColor);
                sb.draw(boss, Settings.WIDTH / 2.0F - BOSS_W / 2.0F,
                        DungeonMapScreen.offsetY + mapOffsetY + BOSS_OFFSET_Y, BOSS_W, BOSS_W);

            } else {

                sb.draw(bossOutline, Settings.WIDTH / 2.0F - BOSS_W / 2.0F,
                        DungeonMapScreen.offsetY + mapOffsetY + BOSS_OFFSET_Y, BOSS_W, BOSS_W);

                sb.setColor(this.bossNodeColor);
                sb.draw(boss, Settings.WIDTH / 2.0F - BOSS_W / 2.0F,
                        DungeonMapScreen.offsetY + mapOffsetY + BOSS_OFFSET_Y, BOSS_W, BOSS_W);
            }
        }

        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MAP) {
            this.bossHb.render(sb);
            if (Settings.isControllerMode && AbstractDungeon.dungeonMapScreen.map.bossHb.hovered) {
                renderReticle(sb, AbstractDungeon.dungeonMapScreen.map.bossHb);
            }
        }
    }

    private void renderMapCenters(SpriteBatch sb) {
        if (!Settings.isMobile) {
            sb.draw(mid, 0.0F, DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH, 1080.0F * Settings.scale);
        } else {
            sb.draw(mid, -Settings.WIDTH * 0.05F, DungeonMapScreen.offsetY + mapOffsetY, Settings.WIDTH * 1.1F,
                    1080.0F * Settings.scale);
        }
    }

    public void renderReticle(SpriteBatch sb, Hitbox hb) {
        float offset = Interpolation.fade.apply(24.0F * Settings.scale, 12.0F * Settings.scale, this.reticleColor.a);
        sb.setColor(this.reticleColor);
        renderReticleCorner(sb, -hb.width / 2.0F + offset, hb.height / 2.0F - offset, hb, false, false);
        renderReticleCorner(sb, hb.width / 2.0F - offset, hb.height / 2.0F - offset, hb, true, false);
        renderReticleCorner(sb, -hb.width / 2.0F + offset, -hb.height / 2.0F + offset, hb, false, true);
        renderReticleCorner(sb, hb.width / 2.0F - offset, -hb.height / 2.0F + offset, hb, true, true);
    }

    private void renderReticleCorner(SpriteBatch sb, float x, float y, Hitbox hb, boolean flipX, boolean flipY) {
        sb.draw(ImageMaster.RETICLE_CORNER, hb.cX + x - 18.0F, hb.cY + y - 18.0F, 18.0F, 18.0F, 36.0F, 36.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 36, 36, flipX, flipY);
    }

    private void renderMapBlender(SpriteBatch sb) {
        if (!AbstractDungeon.id.equals("TheEnding"))
            if (!Settings.isMobile) {
                sb.draw(blend, 0.0F, DungeonMapScreen.offsetY + mapOffsetY + 800.0F * Settings.scale, Settings.WIDTH,
                        BLEND_H);

                sb.draw(blend, 0.0F, DungeonMapScreen.offsetY + mapOffsetY - 220.0F * Settings.scale, Settings.WIDTH,
                        BLEND_H);

            } else {

                sb.draw(blend, -Settings.WIDTH * 0.05F, DungeonMapScreen.offsetY + mapOffsetY + 800.0F * Settings.scale,
                        Settings.WIDTH * 1.1F, BLEND_H);

                sb.draw(blend, -Settings.WIDTH * 0.05F, DungeonMapScreen.offsetY + mapOffsetY - 220.0F * Settings.scale,
                        Settings.WIDTH * 1.1F, BLEND_H);
            }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\map\DungeonMap.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

