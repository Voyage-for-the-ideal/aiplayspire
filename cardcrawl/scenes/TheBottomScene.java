package com.megacrit.cardcrawl.scenes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.CampfireUI;
import com.megacrit.cardcrawl.vfx.scene.*;

import java.util.ArrayList;
import java.util.Iterator;

public class TheBottomScene
        extends AbstractScene {
    private boolean renderLeftWall;
    private boolean renderSolidMid;
    private boolean renderHollowMid;
    private TextureAtlas.AtlasRegion fg;
    private TextureAtlas.AtlasRegion mg;
    private TextureAtlas.AtlasRegion leftWall;
    private TextureAtlas.AtlasRegion hollowWall;
    private TextureAtlas.AtlasRegion solidWall;
    private boolean renderCeilingMod1;
    private boolean renderCeilingMod2;
    private Color overlayColor = Color.WHITE.cpy();
    private boolean renderCeilingMod3;
    private boolean renderCeilingMod4;
    private boolean renderCeilingMod5;
    private boolean renderCeilingMod6;
    private TextureAtlas.AtlasRegion ceiling;
    private TextureAtlas.AtlasRegion ceilingMod1;
    private TextureAtlas.AtlasRegion ceilingMod2;
    private TextureAtlas.AtlasRegion ceilingMod3;
    private TextureAtlas.AtlasRegion ceilingMod4;
    private TextureAtlas.AtlasRegion ceilingMod5;
    private TextureAtlas.AtlasRegion ceilingMod6;
    private Color whiteColor = Color.WHITE.cpy();

    private ArrayList<DustEffect> dust = new ArrayList<>();
    private ArrayList<BottomFogEffect> fog = new ArrayList<>();
    private ArrayList<InteractableTorchEffect> torches = new ArrayList<>();
    private static final int DUST_AMT = 24;

    public TheBottomScene() {
        super("bottomScene/scene.atlas");
        this.fg = this.atlas.findRegion("mod/fg");
        this.mg = this.atlas.findRegion("mod/mg");
        this.leftWall = this.atlas.findRegion("mod/mod1");
        this.hollowWall = this.atlas.findRegion("mod/mod2");
        this.solidWall = this.atlas.findRegion("mod/midWall");

        this.ceiling = this.atlas.findRegion("mod/ceiling");
        this.ceilingMod1 = this.atlas.findRegion("mod/ceilingMod1");
        this.ceilingMod2 = this.atlas.findRegion("mod/ceilingMod2");
        this.ceilingMod3 = this.atlas.findRegion("mod/ceilingMod3");
        this.ceilingMod4 = this.atlas.findRegion("mod/ceilingMod4");
        this.ceilingMod5 = this.atlas.findRegion("mod/ceilingMod5");
        this.ceilingMod6 = this.atlas.findRegion("mod/ceilingMod6");

        this.ambianceName = "AMBIANCE_BOTTOM";
        fadeInAmbiance();
    }

    public void update() {
        super.update();
        updateDust();
        updateFog();
        updateTorches();
    }

    private void updateDust() {
        for (Iterator<DustEffect> e = this.dust.iterator(); e.hasNext();) {
            DustEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }

        if (this.dust.size() < 96 && !Settings.DISABLE_EFFECTS) {
            this.dust.add(new DustEffect());
        }
    }

    private void updateFog() {
        if (this.fog.size() < 50 && !Settings.DISABLE_EFFECTS) {
            this.fog.add(new BottomFogEffect(true));
        }

        for (Iterator<BottomFogEffect> e = this.fog.iterator(); e.hasNext();) {
            BottomFogEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }
    }

    private void updateTorches() {
        for (Iterator<InteractableTorchEffect> e = this.torches.iterator(); e.hasNext();) {
            InteractableTorchEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }
    }

    public void nextRoom(AbstractRoom room) {
        super.nextRoom(room);
        randomizeScene();

        if (room instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            CardCrawlGame.music.silenceBGM();
        }

        if (room instanceof com.megacrit.cardcrawl.rooms.EventRoom
                || room instanceof com.megacrit.cardcrawl.rooms.RestRoom) {
            this.torches.clear();
        }

        fadeInAmbiance();
    }

    public void randomizeScene() {
        if (MathUtils.randomBoolean()) {
            this.renderSolidMid = false;
            this.renderLeftWall = false;
            this.renderHollowMid = true;
            if (MathUtils.randomBoolean()) {
                this.renderSolidMid = true;
                if (MathUtils.randomBoolean()) {
                    this.renderLeftWall = true;
                }
            }
        } else {
            this.renderLeftWall = false;
            this.renderHollowMid = false;
            this.renderSolidMid = true;
            if (MathUtils.randomBoolean()) {
                this.renderLeftWall = true;
            }
        }

        this.renderCeilingMod1 = MathUtils.randomBoolean();
        this.renderCeilingMod2 = MathUtils.randomBoolean();
        this.renderCeilingMod3 = MathUtils.randomBoolean();
        this.renderCeilingMod4 = MathUtils.randomBoolean();
        this.renderCeilingMod5 = MathUtils.randomBoolean();
        this.renderCeilingMod6 = MathUtils.randomBoolean();

        randomizeTorch();
        this.overlayColor.r = MathUtils.random(0.0F, 0.05F);
        this.overlayColor.g = MathUtils.random(0.0F, 0.2F);
        this.overlayColor.b = MathUtils.random(0.0F, 0.2F);
    }

    public void renderCombatRoomBg(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.bg, true);

        if (!this.isCamp) {
            for (BottomFogEffect e : this.fog) {
                e.render(sb);
            }
        }

        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.mg, true);
        if (this.renderHollowMid && (this.renderSolidMid || this.renderLeftWall)) {
            sb.setColor(Color.GRAY);
        }
        renderAtlasRegionIf(sb, this.solidWall, this.renderSolidMid);
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.hollowWall, this.renderHollowMid);
        renderAtlasRegionIf(sb, this.leftWall, this.renderLeftWall);

        renderAtlasRegionIf(sb, this.ceiling, true);
        renderAtlasRegionIf(sb, this.ceilingMod1, this.renderCeilingMod1);
        renderAtlasRegionIf(sb, this.ceilingMod2, this.renderCeilingMod2);
        renderAtlasRegionIf(sb, this.ceilingMod3, this.renderCeilingMod3);
        renderAtlasRegionIf(sb, this.ceilingMod4, this.renderCeilingMod4);
        renderAtlasRegionIf(sb, this.ceilingMod5, this.renderCeilingMod5);
        renderAtlasRegionIf(sb, this.ceilingMod6, this.renderCeilingMod6);

        for (InteractableTorchEffect e : this.torches) {
            e.render(sb);
        }

        sb.setBlendFunction(768, 1);
        sb.setColor(this.overlayColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        sb.setBlendFunction(770, 771);
    }

    private void randomizeTorch() {
        this.torches.clear();

        if (MathUtils.randomBoolean(0.1F)) {
            this.torches.add(new InteractableTorchEffect(1790.0F * Settings.xScale, 850.0F * Settings.yScale,
                    InteractableTorchEffect.TorchSize.S));
        }

        if (this.renderHollowMid && !this.renderSolidMid) {
            int roll = MathUtils.random(2);
            if (roll == 0) {
                this.torches.add(new InteractableTorchEffect(800.0F * Settings.xScale, 768.0F * Settings.yScale));
                this.torches.add(new InteractableTorchEffect(1206.0F * Settings.xScale, 768.0F * Settings.yScale));
            } else if (roll == 1) {
                this.torches.add(new InteractableTorchEffect(328.0F * Settings.xScale, 865.0F * Settings.yScale,
                        InteractableTorchEffect.TorchSize.S));
            }
        } else if (!this.renderLeftWall && !this.renderHollowMid) {
            if (MathUtils.randomBoolean(0.75F)) {
                this.torches.add(new InteractableTorchEffect(613.0F * Settings.xScale, 860.0F * Settings.yScale));
                this.torches.add(new InteractableTorchEffect(613.0F * Settings.xScale, 672.0F * Settings.yScale));
                if (MathUtils.randomBoolean(0.3F)) {
                    this.torches.add(new InteractableTorchEffect(1482.0F * Settings.xScale, 860.0F * Settings.yScale));
                    this.torches.add(new InteractableTorchEffect(1482.0F * Settings.xScale, 672.0F * Settings.yScale));
                }
            }
        } else if (this.renderSolidMid && this.renderHollowMid) {
            if (!this.renderLeftWall) {
                int roll = MathUtils.random(3);
                if (roll == 0) {
                    this.torches.add(new InteractableTorchEffect(912.0F * Settings.xScale, 790.0F * Settings.yScale));
                    this.torches.add(new InteractableTorchEffect(912.0F * Settings.xScale, 526.0F * Settings.yScale));
                    this.torches.add(new InteractableTorchEffect(844.0F * Settings.xScale, 658.0F * Settings.yScale,
                            InteractableTorchEffect.TorchSize.S));
                    this.torches.add(new InteractableTorchEffect(980.0F * Settings.xScale, 658.0F * Settings.yScale,
                            InteractableTorchEffect.TorchSize.S));
                } else if (roll == 1 || roll == 2) {
                    this.torches.add(new InteractableTorchEffect(1828.0F * Settings.xScale, 720.0F * Settings.yScale));
                }

            } else if (MathUtils.randomBoolean(0.75F)) {
                this.torches.add(new InteractableTorchEffect(970.0F * Settings.xScale, 874.0F * Settings.yScale,
                        InteractableTorchEffect.TorchSize.L));
            }

        } else if (this.renderLeftWall && !this.renderHollowMid &&
                MathUtils.randomBoolean(0.75F)) {
            this.torches.add(new InteractableTorchEffect(970.0F * Settings.xScale, 873.0F * Settings.renderScale,
                    InteractableTorchEffect.TorchSize.L));
            this.torches.add(new InteractableTorchEffect(616.0F * Settings.xScale, 813.0F * Settings.renderScale));
            this.torches.add(new InteractableTorchEffect(1266.0F * Settings.xScale, 708.0F * Settings.renderScale));
        }

        LightFlareMEffect.renderGreen = MathUtils.randomBoolean();
        TorchParticleMEffect.renderGreen = LightFlareMEffect.renderGreen;
    }

    public void renderCombatRoomFg(SpriteBatch sb) {
        if (!this.isCamp) {
            for (DustEffect e : this.dust) {
                e.render(sb);
            }
        }
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.fg, true);
    }

    public void renderCampfireRoom(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.campfireBg, true);
        sb.setBlendFunction(770, 1);
        this.whiteColor.a = MathUtils.cosDeg((float) (System.currentTimeMillis() / 3L % 360L)) / 10.0F + 0.8F;
        sb.setColor(this.whiteColor);
        renderQuadrupleSize(sb, this.campfireGlow, !CampfireUI.hidden);
        sb.setBlendFunction(770, 771);
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.campfireKindling, true);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\scenes\
 * TheBottomScene.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

