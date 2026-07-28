package com.megacrit.cardcrawl.monsters.exordium;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.localization.MonsterStrings;
import com.megacrit.cardcrawl.vfx.BobEffect;
import com.megacrit.cardcrawl.vfx.GhostlyFireEffect;
import com.megacrit.cardcrawl.vfx.GhostlyWeakFireEffect;
import com.megacrit.cardcrawl.vfx.combat.GhostIgniteEffect;

public class HexaghostOrb {
    public static final String ID = "HexaghostOrb";
    private static final MonsterStrings monsterStrings = CardCrawlGame.languagePack.getMonsterStrings("HexaghostOrb");
    public static final String NAME = monsterStrings.NAME;
    public static final String[] MOVES = monsterStrings.MOVES;
    public static final String[] DIALOG = monsterStrings.DIALOG;

    private BobEffect effect = new BobEffect(2.0F);

    private float activateTimer;
    public boolean activated = false;
    public boolean hidden = false;
    private float particleTimer = 0.0F;
    public boolean playedSfx = false;
    private Color color;
    private float x;
    private float y;
    private static final float PARTICLE_INTERVAL = 0.06F;

    public HexaghostOrb(float x, float y, int index) {
        this.x = x * Settings.scale + MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        this.y = y * Settings.scale + MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        this.activateTimer = index * 0.3F;
        this.color = Color.CHARTREUSE.cpy();
        this.color.a = 0.0F;
        this.hidden = true;
    }

    public void activate(float oX, float oY) {
        this.playedSfx = false;
        this.activated = true;
        this.hidden = false;
    }

    public void deactivate() {
        this.activated = false;
    }

    public void hide() {
        this.hidden = true;
    }

    public void update(float oX, float oY) {
        if (!this.hidden) {
            if (this.activated) {
                this.activateTimer -= Gdx.graphics.getDeltaTime();
                if (this.activateTimer < 0.0F) {
                    if (!this.playedSfx) {
                        this.playedSfx = true;
                        AbstractDungeon.effectsQueue.add(new GhostIgniteEffect(this.x + oX, this.y + oY));
                        if (MathUtils.randomBoolean()) {
                            CardCrawlGame.sound.play("GHOST_ORB_IGNITE_1", 0.3F);
                        } else {
                            CardCrawlGame.sound.play("GHOST_ORB_IGNITE_2", 0.3F);
                        }
                    }
                    this.color.a = MathHelper.fadeLerpSnap(this.color.a, 1.0F);
                    this.effect.update();
                    this.effect.update();
                    this.particleTimer -= Gdx.graphics.getDeltaTime();
                    if (this.particleTimer < 0.0F) {
                        AbstractDungeon.effectList.add(new GhostlyFireEffect(this.x + oX + this.effect.y * 2.0F,
                                this.y + oY + this.effect.y * 2.0F));

                        this.particleTimer = 0.06F;
                    }
                }
            } else {
                this.effect.update();
                this.particleTimer -= Gdx.graphics.getDeltaTime();
                if (this.particleTimer < 0.0F) {
                    AbstractDungeon.effectList.add(new GhostlyWeakFireEffect(this.x + oX + this.effect.y * 2.0F,
                            this.y + oY + this.effect.y * 2.0F));

                    this.particleTimer = 0.06F;
                }
            }
        } else {
            this.color.a = MathHelper.fadeLerpSnap(this.color.a, 0.0F);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\monsters\exordium\
 * HexaghostOrb.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

