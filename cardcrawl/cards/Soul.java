package com.megacrit.cardcrawl.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.CardTrailEffect;
import com.megacrit.cardcrawl.vfx.combat.EmpowerEffect;

import java.util.ArrayList;

public class Soul {
    public AbstractCard card;
    public CardGroup group;
    private CatmullRomSpline<Vector2> crs = new CatmullRomSpline();
    private ArrayList<Vector2> controlPoints = new ArrayList<>();
    private static final int NUM_POINTS = 20;
    private Vector2[] points = new Vector2[20];
    private Vector2 pos;
    private Vector2 target;
    private float vfxTimer = 0.015F;
    private static final float VFX_INTERVAL = 0.015F;
    private float backUpTimer;
    private static final float BACK_UP_TIME = 1.5F;
    private float spawnStutterTimer = 0.0F;

    private static final float STUTTER_TIME_MAX = 0.12F;
    private boolean isInvisible = false;

    public static final Pool<CardTrailEffect> trailEffectPool = new Pool<CardTrailEffect>() {
        protected CardTrailEffect newObject() {
            return new CardTrailEffect();
        }
    };

    private static final float DISCARD_X = Settings.WIDTH * 0.96F;
    private static final float DISCARD_Y = Settings.HEIGHT * 0.06F;
    private static final float DRAW_PILE_X = Settings.WIDTH * 0.04F;
    private static final float DRAW_PILE_Y = Settings.HEIGHT * 0.06F;
    private static final float MASTER_DECK_X = Settings.WIDTH - 96.0F * Settings.scale;
    private static final float MASTER_DECK_Y = Settings.HEIGHT - 32.0F * Settings.scale;

    private float currentSpeed = 0.0F;
    private static final float START_VELOCITY = 200.0F * Settings.scale;
    private static final float MAX_VELOCITY = 6000.0F * Settings.scale;
    private static final float VELOCITY_RAMP_RATE = 3000.0F * Settings.scale;
    public boolean isReadyForReuse;
    public boolean isDone;
    private static final float DST_THRESHOLD = 36.0F * Settings.scale;
    private static final float HOME_IN_THRESHOLD = 72.0F * Settings.scale;

    private float rotation;

    private boolean rotateClockwise = true;
    private boolean stopRotating = false;
    private float rotationRate;
    private static final float ROTATION_RATE = 150.0F * Settings.scale;

    private static final float ROTATION_RAMP_RATE = 800.0F;

    private Vector2 tmp = new Vector2();

    public Soul() {
        this.crs.controlPoints = (Vector2[]) new Vector2[1];
        this.isReadyForReuse = true;
    }

    public void discard(AbstractCard card, boolean visualOnly) {
        this.card = card;
        this.group = AbstractDungeon.player.discardPile;
        if (!visualOnly) {
            this.group.addToTop(card);
        }
        this.pos = new Vector2(card.current_x, card.current_y);
        this.target = new Vector2(DISCARD_X, DISCARD_Y);
        setSharedVariables();
        this.rotation = card.angle + 270.0F;
        this.rotateClockwise = false;
        if (Settings.FAST_MODE) {
            this.currentSpeed = START_VELOCITY * MathUtils.random(4.0F, 6.0F);
        } else {
            this.currentSpeed = START_VELOCITY * MathUtils.random(1.0F, 4.0F);
        }
    }

    public void discard(AbstractCard card) {
        discard(card, false);
    }

    public void shuffle(AbstractCard card, boolean isInvisible) {
        this.isInvisible = isInvisible;
        this.card = card;
        this.group = AbstractDungeon.player.drawPile;
        this.group.addToTop(card);
        this.pos = new Vector2(DISCARD_X, DISCARD_Y);
        this.target = new Vector2(DRAW_PILE_X, DRAW_PILE_Y);
        setSharedVariables();
        this.rotation = MathUtils.random(260.0F, 310.0F);
        if (Settings.FAST_MODE) {
            this.currentSpeed = START_VELOCITY * MathUtils.random(8.0F, 12.0F);
        } else {
            this.currentSpeed = START_VELOCITY * MathUtils.random(2.0F, 5.0F);
        }
        this.rotateClockwise = true;
        this.spawnStutterTimer = MathUtils.random(0.0F, 0.12F);
    }

    public void onToDeck(AbstractCard card, boolean randomSpot, boolean visualOnly) {
        this.card = card;
        this.group = AbstractDungeon.player.drawPile;
        if (!visualOnly) {
            if (randomSpot) {
                this.group.addToRandomSpot(card);
            } else {
                this.group.addToTop(card);
            }
        }
        this.pos = new Vector2(card.current_x, card.current_y);
        this.target = new Vector2(DRAW_PILE_X, DRAW_PILE_Y);
        setSharedVariables();
        this.rotation = card.angle + 270.0F;
        this.rotateClockwise = true;
    }

    public void onToDeck(AbstractCard card, boolean randomSpot) {
        onToDeck(card, randomSpot, false);
    }

    public void onToBottomOfDeck(AbstractCard card) {
        this.card = card;
        this.group = AbstractDungeon.player.drawPile;
        this.group.addToBottom(card);
        this.pos = new Vector2(card.current_x, card.current_y);
        this.target = new Vector2(DRAW_PILE_X, DRAW_PILE_Y);
        setSharedVariables();
        this.rotation = card.angle + 270.0F;
        this.rotateClockwise = true;
    }

    public void empower(AbstractCard card) {
        CardCrawlGame.sound.play("CARD_POWER_WOOSH", 0.1F);
        this.card = card;
        this.group = null;
        this.pos = new Vector2(card.current_x, card.current_y);
        this.target = new Vector2(AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY);
        setSharedVariables();
    }

    public void obtain(AbstractCard card) {
        this.card = card;
        this.group = AbstractDungeon.player.masterDeck;
        this.group.addToTop(card);
        if (ModHelper.isModEnabled("Hoarder")) {
            this.group.addToTop(card.makeStatEquivalentCopy());
            this.group.addToTop(card.makeStatEquivalentCopy());
        }
        this.pos = new Vector2(card.current_x, card.current_y);
        this.target = new Vector2(MASTER_DECK_X, MASTER_DECK_Y);
        setSharedVariables();
    }

    private void setSharedVariables() {
        this.controlPoints.clear();
        if (Settings.FAST_MODE) {
            this.rotationRate = ROTATION_RATE * MathUtils.random(4.0F, 6.0F);
            this.currentSpeed = START_VELOCITY * MathUtils.random(1.0F, 1.5F);
            this.backUpTimer = 0.5F;
        } else {
            this.rotationRate = ROTATION_RATE * MathUtils.random(1.0F, 2.0F);
            this.currentSpeed = START_VELOCITY * MathUtils.random(0.2F, 1.0F);
            this.backUpTimer = 1.5F;
        }

        this.stopRotating = false;
        this.rotateClockwise = MathUtils.randomBoolean();
        this.rotation = MathUtils.random(0, 359);
        this.isReadyForReuse = false;
        this.isDone = false;
    }

    public void update() {
        if (isCarryingCard()) {
            this.card.update();
            this.card.targetAngle = this.rotation + 90.0F;
            this.card.current_x = this.pos.x;
            this.card.current_y = this.pos.y;
            this.card.target_x = this.card.current_x;
            this.card.target_y = this.card.current_y;

            if (this.spawnStutterTimer > 0.0F) {
                this.spawnStutterTimer -= Gdx.graphics.getDeltaTime();

                return;
            }
            updateMovement();
            updateBackUpTimer();
        } else {
            this.isDone = true;
        }

        if (this.isDone) {

            if (this.group == null) {
                AbstractDungeon.effectList
                        .add(new EmpowerEffect(AbstractDungeon.player.hb.cX, AbstractDungeon.player.hb.cY));

                this.isReadyForReuse = true;

                return;
            }
            switch (this.group.type) {
                case MASTER_DECK:
                    this.card.setAngle(0.0F);
                    this.card.targetDrawScale = 0.75F;
                    break;
                case DRAW_PILE:
                    this.card.targetDrawScale = 0.75F;
                    this.card.setAngle(0.0F);
                    this.card.lighten(false);
                    this.card.clearPowers();
                    AbstractDungeon.overlayMenu.combatDeckPanel.pop();
                    break;
                case DISCARD_PILE:
                    this.card.targetDrawScale = 0.75F;
                    this.card.setAngle(0.0F);
                    this.card.lighten(false);
                    this.card.clearPowers();
                    this.card.teleportToDiscardPile();
                    AbstractDungeon.overlayMenu.discardPilePanel.pop();
                    break;
            }

            if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
                AbstractDungeon.player.hand.applyPowers();
            }
            this.isReadyForReuse = true;
        }
    }

    private boolean isCarryingCard() {
        if (this.group == null) {
            return true;
        }

        switch (this.group.type) {
            case DRAW_PILE:
                if (AbstractDungeon.player.drawPile.contains(this.card)) {
                    return true;
                }
                return false;

            case DISCARD_PILE:
                if (AbstractDungeon.player.discardPile.contains(this.card)) {
                    return true;
                }
                return false;
        }

        return true;
    }

    private void updateMovement() {
        this.pos.x -= this.target.x;
        this.pos.y -= this.target.y;
        this.tmp.nor();
        float targetAngle = this.tmp.angle();
        this.rotationRate += Gdx.graphics.getDeltaTime() * 800.0F;

        if (!this.stopRotating) {
            if (this.rotateClockwise) {
                this.rotation += Gdx.graphics.getDeltaTime() * this.rotationRate;
            } else {
                this.rotation -= Gdx.graphics.getDeltaTime() * this.rotationRate;
                if (this.rotation < 0.0F) {
                    this.rotation += 360.0F;
                }
            }

            this.rotation %= 360.0F;

            if (!this.stopRotating) {
                if (this.target.dst(this.pos) < HOME_IN_THRESHOLD) {
                    this.rotation = targetAngle;
                    this.stopRotating = true;
                } else if (Math.abs(this.rotation - targetAngle) < Gdx.graphics.getDeltaTime() * this.rotationRate) {
                    this.rotation = targetAngle;
                    this.stopRotating = true;
                }
            }
        }

        this.tmp.setAngle(this.rotation);

        this.tmp.x *= Gdx.graphics.getDeltaTime() * this.currentSpeed;
        this.tmp.y *= Gdx.graphics.getDeltaTime() * this.currentSpeed;
        this.pos.sub(this.tmp);

        if (this.stopRotating && this.backUpTimer < 1.3499999F) {
            this.currentSpeed += Gdx.graphics.getDeltaTime() * VELOCITY_RAMP_RATE * 3.0F;
        } else {
            this.currentSpeed += Gdx.graphics.getDeltaTime() * VELOCITY_RAMP_RATE * 1.5F;
        }
        if (this.currentSpeed > MAX_VELOCITY) {
            this.currentSpeed = MAX_VELOCITY;
        }

        if (this.target.x < Settings.WIDTH / 2.0F && this.pos.x < 0.0F) {
            this.isDone = true;
        } else if (this.target.x > Settings.WIDTH / 2.0F && this.pos.x > Settings.WIDTH) {
            this.isDone = true;
        }

        if (this.target.dst(this.pos) < DST_THRESHOLD) {
            this.isDone = true;
        }

        this.vfxTimer -= Gdx.graphics.getDeltaTime();
        if (!this.isDone && this.vfxTimer < 0.0F) {
            this.vfxTimer = 0.015F;
            if (!this.controlPoints.isEmpty()) {
                if (!((Vector2) this.controlPoints.get(0)).equals(this.pos)) {
                    this.controlPoints.add(this.pos.cpy());
                }
            } else {
                this.controlPoints.add(this.pos.cpy());
            }

            if (this.controlPoints.size() > 10) {
                this.controlPoints.remove(0);
            }

            if (this.controlPoints.size() > 3) {
                Vector2[] vec2Array = new Vector2[0];
                this.crs.set((Vector2[]) this.controlPoints.toArray((Object[]) vec2Array), false);

                for (int i = 0; i < 20; i++) {
                    if (this.points[i] == null) {
                        this.points[i] = new Vector2();
                    }
                    Vector2 derp = (Vector2) this.crs.valueAt((Vector2) this.points[i], i / 19.0F);
                    CardTrailEffect effect = (CardTrailEffect) trailEffectPool.obtain();
                    effect.init(derp.x, derp.y);
                    AbstractDungeon.topLevelEffects.add(effect);
                }
            }
        }
    }

    private void updateBackUpTimer() {
        this.backUpTimer -= Gdx.graphics.getDeltaTime();
        if (this.backUpTimer < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isInvisible) {
            this.card.renderOuterGlow(sb);
            this.card.render(sb);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\Soul.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */



