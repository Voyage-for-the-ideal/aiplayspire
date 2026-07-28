package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.NewQueueCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.TutorialStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.EndTurnGlowEffect;
import com.megacrit.cardcrawl.vfx.EndTurnLongPressBarFlashEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class EndTurnButton {
    private static final TutorialStrings tutorialStrings = CardCrawlGame.languagePack.getTutorialString("End Turn Tip");
    public static final String[] MSG = tutorialStrings.TEXT;
    public static final String[] LABEL = tutorialStrings.LABEL;
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("End Turn Button");
    public static final String[] TEXT = uiStrings.TEXT;

    private String label = TEXT[0];
    public static final String END_TURN_MSG = TEXT[0];
    public static final String ENEMY_TURN_MSG = TEXT[1];
    private static final Color DISABLED_COLOR = new Color(0.7F, 0.7F, 0.7F, 1.0F);
    private static final float SHOW_X = 1640.0F * Settings.xScale, SHOW_Y = 210.0F * Settings.yScale;
    private static final float HIDE_X = SHOW_X + 500.0F * Settings.xScale;
    private float current_x = HIDE_X, current_y = SHOW_Y;
    private float target_x = this.current_x;

    private boolean isHidden = true;

    public boolean enabled = false;
    private boolean isDisabled = false;
    private Color textColor;
    private ArrayList<EndTurnGlowEffect> glowList = new ArrayList<>();
    private static final float GLOW_INTERVAL = 1.2F;
    private float glowTimer = 0.0F;

    public boolean isGlowing = false;
    public boolean isWarning = false;
    private Hitbox hb = new Hitbox(0.0F, 0.0F, 230.0F * Settings.scale, 110.0F * Settings.scale);

    private float holdProgress = 0.0F;
    private static final float HOLD_DUR = 0.4F;
    private Color holdBarColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);

    public void update() {
        glow();
        updateHoldProgress();

        if (this.current_x != this.target_x) {
            this.current_x = MathUtils.lerp(this.current_x, this.target_x, Gdx.graphics.getDeltaTime() * 9.0F);
            if (Math.abs(this.current_x - this.target_x) < Settings.UI_SNAP_THRESHOLD) {
                this.current_x = this.target_x;
            }
        }

        this.hb.move(this.current_x, this.current_y);

        if (this.enabled) {
            this.isDisabled = false;
            if (AbstractDungeon.isScreenUp || AbstractDungeon.player.isDraggingCard
                    || AbstractDungeon.player.inSingleTargetMode) {
                this.isDisabled = true;
            }

            if (AbstractDungeon.player.hoveredCard == null) {
                this.hb.update();
            }

            if (!Settings.USE_LONG_PRESS && InputHelper.justClickedLeft && this.hb.hovered && !this.isDisabled
                    && !AbstractDungeon.isScreenUp) {

                this.hb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            }

            if (this.hb.hovered && !this.isDisabled && !AbstractDungeon.isScreenUp) {
                this.isWarning = showWarning();
                if (this.hb.justHovered && AbstractDungeon.player.hoveredCard == null) {
                    CardCrawlGame.sound.play("UI_HOVER");
                    for (AbstractCard c : AbstractDungeon.player.hand.group) {
                        if (c.isGlowing) {
                            c.superFlash(c.glowColor);
                        }
                    }
                }
            }
        }

        if (this.holdProgress == 0.4F && !this.isDisabled && !AbstractDungeon.isScreenUp) {
            disable(true);
            this.holdProgress = 0.0F;
            AbstractDungeon.effectsQueue.add(new EndTurnLongPressBarFlashEffect());
        }

        if ((!Settings.USE_LONG_PRESS || (!Settings.isControllerMode && !InputActionSet.endTurn.isPressed()))
                && (this.hb.clicked || ((InputActionSet.endTurn
                        .isJustPressed() || CInputActionSet.proceed.isJustPressed()) && !this.isDisabled
                        && this.enabled))) {

            this.hb.clicked = false;

            if (!this.isDisabled && !AbstractDungeon.isScreenUp) {
                disable(true);
            }
        }
    }

    private void updateHoldProgress() {
        if (!Settings.USE_LONG_PRESS
                || (!Settings.isControllerMode && !InputActionSet.endTurn.isPressed() && !InputHelper.isMouseDown)) {

            this.holdProgress -= Gdx.graphics.getDeltaTime();
            if (this.holdProgress < 0.0F) {
                this.holdProgress = 0.0F;
            }

            return;
        }
        if (((this.hb.hovered && InputHelper.isMouseDown) || CInputActionSet.proceed.isPressed()
                || InputActionSet.endTurn
                        .isPressed())
                && !this.isDisabled && this.enabled) {
            this.holdProgress += Gdx.graphics.getDeltaTime();
            if (this.holdProgress > 0.4F) {
                this.holdProgress = 0.4F;
            }
        } else {
            this.holdProgress -= Gdx.graphics.getDeltaTime();
            if (this.holdProgress < 0.0F) {
                this.holdProgress = 0.0F;
            }
        }
    }

    private boolean showWarning() {
        for (AbstractCard card : AbstractDungeon.player.hand.group) {
            if (card.isGlowing) {
                return true;
            }
        }
        return false;
    }

    public void enable() {
        this.enabled = true;
        updateText(END_TURN_MSG);
    }

    public void disable(boolean isEnemyTurn) {
        InputHelper.moveCursorToNeutralPosition();
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new NewQueueCardAction());

        this.enabled = false;
        this.hb.hovered = false;
        this.isGlowing = false;

        if (isEnemyTurn) {
            updateText(ENEMY_TURN_MSG);
            CardCrawlGame.sound.play("END_TURN");
            AbstractDungeon.player.endTurnQueued = true;
            AbstractDungeon.player.releaseCard();
        } else {
            updateText(END_TURN_MSG);
        }
    }

    public void disable() {
        this.enabled = false;
        this.hb.hovered = false;
        this.isGlowing = false;
    }

    public void updateText(String msg) {
        this.label = msg;
    }

    private void glow() {
        if (this.isGlowing && !this.isHidden) {
            if (this.glowTimer < 0.0F) {
                this.glowList.add(new EndTurnGlowEffect());
                this.glowTimer = 1.2F;
            } else {
                this.glowTimer -= Gdx.graphics.getDeltaTime();
            }
        }

        for (Iterator<EndTurnGlowEffect> i = this.glowList.iterator(); i.hasNext();) {
            AbstractGameEffect e = (AbstractGameEffect) i.next();
            e.update();
            if (e.isDone) {
                i.remove();
            }
        }
    }

    public void hide() {
        if (!this.isHidden) {
            this.target_x = HIDE_X;
            this.isHidden = true;
        }
    }

    public void show() {
        if (this.isHidden) {
            this.target_x = SHOW_X;
            this.isHidden = false;
            if (this.isGlowing) {
                this.glowTimer = -1.0F;
            }
        }
    }

    public void render(SpriteBatch sb) {
        if (!Settings.hideEndTurn) {
            Texture buttonImg;
            float tmpY = this.current_y;
            renderHoldEndTurn(sb);

            if (this.isDisabled || !this.enabled) {
                if (this.label.equals(ENEMY_TURN_MSG)) {
                    this.textColor = Settings.CREAM_COLOR;
                } else {
                    this.textColor = Color.LIGHT_GRAY;
                }
            } else {
                if (this.hb.hovered) {
                    if (this.isWarning) {
                        this.textColor = Settings.RED_TEXT_COLOR;
                    } else {
                        this.textColor = Color.CYAN;
                    }

                } else if (this.isGlowing) {
                    this.textColor = Settings.GOLD_COLOR;
                } else {
                    this.textColor = Settings.CREAM_COLOR;
                }

                if (this.hb.hovered && !AbstractDungeon.isScreenUp && !Settings.isTouchScreen) {
                    TipHelper
                            .renderGenericTip(this.current_x - 90.0F * Settings.scale,
                                    this.current_y + 300.0F * Settings.scale, LABEL[0] + " (" + InputActionSet.endTurn

                                            .getKeyString() + ")",
                                    MSG[0] + AbstractDungeon.player.gameHandSize + MSG[1]);
                }
            }

            if (this.hb.clickStarted && !AbstractDungeon.isScreenUp) {
                tmpY -= 2.0F * Settings.scale;
            } else if (this.hb.hovered && !AbstractDungeon.isScreenUp) {
                tmpY += 2.0F * Settings.scale;
            }

            if (this.enabled) {
                if (this.isDisabled || (this.hb.clickStarted && this.hb.hovered)) {
                    sb.setColor(DISABLED_COLOR);
                } else {
                    sb.setColor(Color.WHITE);
                }
            } else {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
            }

            if (this.isGlowing && !this.hb.clickStarted) {
                buttonImg = ImageMaster.END_TURN_BUTTON_GLOW;
            } else {
                buttonImg = ImageMaster.END_TURN_BUTTON;
            }
            if (this.hb.hovered && !this.isDisabled && !AbstractDungeon.isScreenUp) {
                sb.draw(ImageMaster.END_TURN_HOVER, this.current_x - 128.0F, tmpY - 128.0F, 128.0F, 128.0F, 256.0F,
                        256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, false, false);
            }

            sb.draw(buttonImg, this.current_x - 128.0F, tmpY - 128.0F, 128.0F, 128.0F, 256.0F, 256.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 256, 256, false, false);

            if (!this.enabled) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT);
            }

            renderGlowEffect(sb, this.current_x, this.current_y);

            if ((this.hb.hovered || this.holdProgress > 0.0F) && !this.isDisabled && !AbstractDungeon.isScreenUp) {
                sb.setBlendFunction(770, 1);
                sb.setColor(Settings.HALF_TRANSPARENT_WHITE_COLOR);
                sb.draw(buttonImg, this.current_x - 128.0F, tmpY - 128.0F, 128.0F, 128.0F, 256.0F, 256.0F,
                        Settings.scale, Settings.scale, 0.0F, 0, 0, 256, 256, false, false);

                sb.setBlendFunction(770, 771);
            }

            if (Settings.isControllerMode && this.enabled) {
                sb.setColor(Color.WHITE);
                sb.draw(CInputActionSet.proceed
                        .getKeyImg(),
                        this.current_x - 32.0F - 42.0F * Settings.scale -
                                FontHelper.getSmartWidth(FontHelper.panelEndTurnFont, this.label, 99999.0F, 0.0F)
                                        / 2.0F,
                        tmpY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64,
                        false, false);
            }

            FontHelper.renderFontCentered(sb, FontHelper.panelEndTurnFont, this.label,
                    this.current_x - 0.0F * Settings.scale, tmpY - 3.0F * Settings.scale, this.textColor);

            if (!this.isHidden) {
                this.hb.render(sb);
            }
        }
    }

    private void renderHoldEndTurn(SpriteBatch sb) {
        if (!Settings.USE_LONG_PRESS) {
            return;
        }

        this.holdBarColor.r = 0.0F;
        this.holdBarColor.g = 0.0F;
        this.holdBarColor.b = 0.0F;
        this.holdBarColor.a = this.holdProgress * 1.5F;
        sb.setColor(this.holdBarColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, this.current_x - 107.0F * Settings.scale,
                this.current_y + 53.0F * Settings.scale - 7.0F * Settings.scale,
                525.0F * Settings.scale * this.holdProgress + 14.0F * Settings.scale, 20.0F * Settings.scale);

        this.holdBarColor.r = this.holdProgress * 2.5F;
        this.holdBarColor.g = 0.6F + this.holdProgress;
        this.holdBarColor.b = 0.6F;
        this.holdBarColor.a = 1.0F;
        sb.setColor(this.holdBarColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, this.current_x - 100.0F * Settings.scale,
                this.current_y + 53.0F * Settings.scale, 525.0F * Settings.scale * this.holdProgress,
                6.0F * Settings.scale);
    }

    private void renderGlowEffect(SpriteBatch sb, float x, float y) {
        for (EndTurnGlowEffect e : this.glowList)
            e.render(sb, x, y);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * EndTurnButton.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

