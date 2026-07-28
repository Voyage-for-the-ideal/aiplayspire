package com.megacrit.cardcrawl.scenes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.CampfireUI;
import com.megacrit.cardcrawl.vfx.scene.CeilingDustEffect;
import com.megacrit.cardcrawl.vfx.scene.FireFlyEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class TheCityScene
        extends AbstractScene {
    private final TextureAtlas.AtlasRegion bg;
    private final TextureAtlas.AtlasRegion bgGlow;
    private final TextureAtlas.AtlasRegion bgGlow2;
    private final TextureAtlas.AtlasRegion bg2;
    private final TextureAtlas.AtlasRegion bg2Glow;
    private final TextureAtlas.AtlasRegion floor;
    private final TextureAtlas.AtlasRegion ceiling;
    private final TextureAtlas.AtlasRegion wall;
    private final TextureAtlas.AtlasRegion chains;
    private final TextureAtlas.AtlasRegion chainsGlow;
    private final TextureAtlas.AtlasRegion pillar1;
    private Color overlayColor = Color.WHITE.cpy();
    private final TextureAtlas.AtlasRegion pillar2;
    private final TextureAtlas.AtlasRegion pillar3;
    private final TextureAtlas.AtlasRegion pillar4;
    private final TextureAtlas.AtlasRegion pillar5;
    private final TextureAtlas.AtlasRegion throne;
    private final TextureAtlas.AtlasRegion throneGlow;
    private final TextureAtlas.AtlasRegion mg;
    private final TextureAtlas.AtlasRegion mgGlow;
    private final TextureAtlas.AtlasRegion mg2;
    private final TextureAtlas.AtlasRegion fg;
    private final TextureAtlas.AtlasRegion fgGlow;
    private final TextureAtlas.AtlasRegion fg2;
    private Color whiteColor = Color.WHITE.cpy();
    private Color yellowTint = new Color(1.0F, 1.0F, 0.9F, 1.0F);
    private boolean renderAltBg;
    private boolean renderMg;
    private boolean renderMgGlow;
    private boolean renderMgAlt;
    private boolean renderWall;
    private boolean renderChains;
    private boolean renderThrone;
    private boolean renderFg2;
    private boolean darkDay;
    private PillarConfig pillarConfig = PillarConfig.OPEN;

    private float ceilingDustTimer = 1.0F;
    private ArrayList<FireFlyEffect> fireFlies = new ArrayList<>();
    private boolean hasFlies;
    private boolean blueFlies;

    public TheCityScene() {
        super("cityScene/scene.atlas");
        this.bg = this.atlas.findRegion("mod/bg1");
        this.bgGlow = this.atlas.findRegion("mod/bgGlowv2");
        this.bgGlow2 = this.atlas.findRegion("mod/bgGlowBlur");
        this.bg2 = this.atlas.findRegion("mod/bg2");
        this.bg2Glow = this.atlas.findRegion("mod/bg2Glow");
        this.floor = this.atlas.findRegion("mod/floor");
        this.ceiling = this.atlas.findRegion("mod/ceiling");
        this.wall = this.atlas.findRegion("mod/wall");
        this.chains = this.atlas.findRegion("mod/chains");
        this.chainsGlow = this.atlas.findRegion("mod/chainsGlow");
        this.pillar1 = this.atlas.findRegion("mod/p1");
        this.pillar2 = this.atlas.findRegion("mod/p2");
        this.pillar3 = this.atlas.findRegion("mod/p3");
        this.pillar4 = this.atlas.findRegion("mod/p4");
        this.pillar5 = this.atlas.findRegion("mod/p5");
        this.throne = this.atlas.findRegion("mod/throne");
        this.throneGlow = this.atlas.findRegion("mod/throneGlow");
        this.mg = this.atlas.findRegion("mod/mg1");
        this.mgGlow = this.atlas.findRegion("mod/mg1Glow");
        this.mg2 = this.atlas.findRegion("mod/mg2");
        this.fg = this.atlas.findRegion("mod/fg");
        this.fgGlow = this.atlas.findRegion("mod/fgGlow");
        this.fg2 = this.atlas.findRegion("mod/fgHideWindow");

        this.ambianceName = "AMBIANCE_CITY";
        fadeInAmbiance();
    }

    private enum PillarConfig {
        OPEN, SIDES_ONLY, FULL, LEFT_1, LEFT_2;
    }

    public void update() {
        super.update();
        updateFireFlies();
        if (!(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.RestRoom) &&
                !(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.EventRoom)) {
            updateParticles();
        }
    }

    private void updateFireFlies() {
        for (Iterator<FireFlyEffect> e = this.fireFlies.iterator(); e.hasNext();) {
            FireFlyEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }

        if (this.fireFlies.size() < 9 && !Settings.DISABLE_EFFECTS &&
                MathUtils.randomBoolean(0.1F)) {
            if (this.blueFlies) {
                this.fireFlies.add(new FireFlyEffect(new Color(

                        MathUtils.random(0.1F, 0.2F),
                        MathUtils.random(0.6F, 0.8F),
                        MathUtils.random(0.8F, 1.0F), 1.0F)));
            } else {

                this.fireFlies.add(new FireFlyEffect(new Color(

                        MathUtils.random(0.8F, 1.0F),
                        MathUtils.random(0.5F, 0.8F),
                        MathUtils.random(0.3F, 0.5F), 1.0F)));
            }
        }
    }

    private void updateParticles() {
        if (Settings.DISABLE_EFFECTS) {
            return;
        }

        this.ceilingDustTimer -= Gdx.graphics.getDeltaTime();
        if (this.ceilingDustTimer < 0.0F) {
            int roll = MathUtils.random(4);
            if (roll == 0) {
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                playDustSfx(false);
            } else if (roll == 1) {
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                playDustSfx(false);
            } else {
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                AbstractDungeon.effectsQueue.add(new CeilingDustEffect());
                if (!Settings.isBackgrounded) {
                    playDustSfx(true);
                }
            }
            this.ceilingDustTimer = MathUtils.random(0.5F, 60.0F);
        }
    }

    private void playDustSfx(boolean boom) {
        if (boom) {
            int roll = MathUtils.random(2);
            if (roll == 0) {
                CardCrawlGame.sound.play("CEILING_BOOM_1", 0.2F);
            } else if (roll == 1) {
                CardCrawlGame.sound.play("CEILING_BOOM_2", 0.2F);
            } else {
                CardCrawlGame.sound.play("CEILING_BOOM_3", 0.2F);
            }
        } else {
            int roll = MathUtils.random(2);
            if (roll == 0) {
                CardCrawlGame.sound.play("CEILING_DUST_1", 0.2F);
            } else if (roll == 1) {
                CardCrawlGame.sound.play("CEILING_DUST_2", 0.2F);
            } else {
                CardCrawlGame.sound.play("CEILING_DUST_3", 0.2F);
            }
        }
    }

    public void randomizeScene() {
        this.hasFlies = MathUtils.randomBoolean();
        this.blueFlies = MathUtils.randomBoolean();

        this.overlayColor.r = MathUtils.random(0.8F, 0.9F);
        this.overlayColor.g = MathUtils.random(0.8F, 0.9F);
        this.overlayColor.b = MathUtils.random(0.95F, 1.0F);

        this.darkDay = MathUtils.randomBoolean(0.33F);

        if (this.darkDay) {
            this.overlayColor.r = 0.6F;
            this.overlayColor.g = MathUtils.random(0.7F, 0.8F);
            this.overlayColor.b = MathUtils.random(0.8F, 0.95F);
        }

        this.renderAltBg = MathUtils.randomBoolean();

        this.renderMg = true;
        if (this.renderMg) {
            this.renderMgAlt = MathUtils.randomBoolean();
            if (!this.renderMgAlt) {
                this.renderMgGlow = MathUtils.randomBoolean();
            }
        }

        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            this.renderWall = false;
        } else {
            this.renderWall = (MathUtils.random(4) == 4);
        }
        if (this.renderWall) {
            this.renderChains = MathUtils.randomBoolean();
        } else {
            this.renderChains = false;
        }

        this.renderFg2 = MathUtils.randomBoolean();

        if (this.renderWall) {
            int roll = MathUtils.random(2);
            if (roll == 0) {
                this.pillarConfig = PillarConfig.OPEN;
            } else if (roll == 1) {
                this.pillarConfig = PillarConfig.LEFT_1;
            } else {
                this.pillarConfig = PillarConfig.LEFT_2;
            }
        } else {
            int roll = MathUtils.random(2);
            if (roll == 0) {
                this.pillarConfig = PillarConfig.OPEN;
            } else if (roll == 1) {
                this.pillarConfig = PillarConfig.SIDES_ONLY;
            } else {
                this.pillarConfig = PillarConfig.FULL;
            }
        }

        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss
                && (AbstractDungeon.getCurrRoom()).monsters
                        .getMonster("TheCollector") != null) {
            this.renderThrone = true;
        } else {
            this.renderThrone = false;
        }
    }

    public void nextRoom(AbstractRoom room) {
        super.nextRoom(room);
        this.fireFlies.clear();
        randomizeScene();
        if (room instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            CardCrawlGame.music.silenceBGM();
        }
        fadeInAmbiance();
    }

    public void renderCombatRoomBg(SpriteBatch sb) {
        sb.setColor(this.overlayColor);

        renderAtlasRegionIf(sb, this.bg, true);
        sb.setBlendFunction(770, 1);
        renderAtlasRegionIf(sb, this.bgGlow, true);
        if (this.darkDay) {
            sb.setColor(Color.WHITE);
            renderAtlasRegionIf(sb, this.bgGlow2, true);
            renderAtlasRegionIf(sb, this.bgGlow2, true);
        }
        sb.setBlendFunction(770, 771);

        renderAtlasRegionIf(sb, this.bg2, this.renderAltBg);
        sb.setBlendFunction(770, 1);
        renderAtlasRegionIf(sb, this.bg2Glow, this.renderAltBg);
        sb.setBlendFunction(770, 771);

        sb.setColor(this.overlayColor);
        renderAtlasRegionIf(sb, this.floor, true);
        renderAtlasRegionIf(sb, this.ceiling, true);

        renderAtlasRegionIf(sb, this.wall, this.renderWall);
        renderAtlasRegionIf(sb, this.chains, this.renderChains);
        if (this.renderChains) {
            sb.setBlendFunction(770, 1);
            this.whiteColor.a = MathUtils.cosDeg((float) (System.currentTimeMillis() / 1L % 360L)) / 10.0F + 0.9F;
            sb.setColor(this.whiteColor);
            renderAtlasRegionIf(sb, this.chainsGlow, true);
            renderAtlasRegionIf(sb, this.chainsGlow, true);
            sb.setBlendFunction(770, 771);
            sb.setColor(this.overlayColor);
        }

        renderAtlasRegionIf(sb, this.mg, this.renderMg);
        sb.setBlendFunction(770, 1);
        if (this.renderMgGlow) {
            this.whiteColor.a = MathUtils.cosDeg((float) (System.currentTimeMillis() / 10L % 360L)) / 2.0F + 0.5F;
            sb.setColor(this.whiteColor);
            renderAtlasRegionIf(sb, this.mgGlow, this.renderMg);
            renderAtlasRegionIf(sb, this.mgGlow, this.renderMg);
            sb.setColor(this.yellowTint);
        } else {
            renderAtlasRegionIf(sb, this.mgGlow, this.renderMg);
        }
        sb.setBlendFunction(770, 771);
        renderAtlasRegionIf(sb, this.mg2, this.renderMgAlt);

        switch (this.pillarConfig) {

            case SIDES_ONLY:
                renderAtlasRegionIf(sb, this.pillar1, true);
                renderAtlasRegionIf(sb, this.pillar5, true);
                break;
            case FULL:
                renderAtlasRegionIf(sb, this.pillar1, true);
                renderAtlasRegionIf(sb, this.pillar2, true);
                renderAtlasRegionIf(sb, this.pillar3, true);
                renderAtlasRegionIf(sb, this.pillar4, true);
                renderAtlasRegionIf(sb, this.pillar5, true);
                break;
            case LEFT_1:
                renderAtlasRegionIf(sb, this.pillar1, true);
                break;
            case LEFT_2:
                renderAtlasRegionIf(sb, this.pillar1, true);
                renderAtlasRegionIf(sb, this.pillar2, true);
                break;
        }

        renderAtlasRegionIf(sb, this.throne, this.renderThrone);
        sb.setBlendFunction(770, 1);
        renderAtlasRegionIf(sb, this.throneGlow, this.renderThrone);
        sb.setBlendFunction(770, 771);
    }

    public void renderCombatRoomFg(SpriteBatch sb) {
        if (!this.isCamp && this.hasFlies) {
            for (FireFlyEffect e : this.fireFlies) {
                e.render(sb);
            }
        }
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.fg, true);
        sb.setBlendFunction(770, 1);
        renderAtlasRegionIf(sb, this.fgGlow, true);
        sb.setBlendFunction(770, 771);
        renderAtlasRegionIf(sb, this.fg2, this.renderFg2);
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
 * TheCityScene.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

