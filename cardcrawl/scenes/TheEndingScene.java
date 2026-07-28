package com.megacrit.cardcrawl.scenes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.scene.ShinySparkleEffect;
import com.megacrit.cardcrawl.vfx.scene.WobblyCircleEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class TheEndingScene
        extends AbstractScene {
    private ArrayList<AbstractGameEffect> circles = new ArrayList<>();

    public TheEndingScene() {
        super("endingScene/scene.atlas");
        this.ambianceName = "AMBIANCE_BEYOND";
        fadeInAmbiance();
    }

    public void update() {
        super.update();
        updateParticles();
    }

    public void randomizeScene() {
    }

    private void updateParticles() {
        for (Iterator<AbstractGameEffect> e = this.circles.iterator(); e.hasNext();) {
            AbstractGameEffect effect = e.next();
            effect.update();
            if (effect.isDone) {
                e.remove();
            }
        }

        if (!(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TrueVictoryRoom) &&
                this.circles.size() < 72 && !Settings.DISABLE_EFFECTS) {
            if (MathUtils.randomBoolean(0.2F)) {
                this.circles.add(new ShinySparkleEffect());
            } else {
                this.circles.add(new WobblyCircleEffect());
            }
        }
    }

    public void nextRoom(AbstractRoom room) {
        super.nextRoom(room);
        randomizeScene();
        if (room instanceof com.megacrit.cardcrawl.rooms.MonsterRoomBoss) {
            CardCrawlGame.music.silenceBGM();
        }
        fadeInAmbiance();
    }

    public void renderCombatRoomBg(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.bg, true);
    }

    public void renderCombatRoomFg(SpriteBatch sb) {
        if (!this.isCamp) {
            sb.setBlendFunction(770, 1);
            for (AbstractGameEffect e : this.circles) {
                e.render(sb);
            }
            sb.setBlendFunction(770, 771);
        }
    }

    public void renderCampfireRoom(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderAtlasRegionIf(sb, this.campfireBg, true);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\scenes\
 * TheEndingScene.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

