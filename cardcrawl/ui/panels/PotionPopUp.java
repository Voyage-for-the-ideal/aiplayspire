package com.megacrit.cardcrawl.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Bezier;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.HandCheckAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.TutorialStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.SmokeBomb;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;

import java.util.ArrayList;

public class PotionPopUp {
    private static final TutorialStrings tutorialStrings = CardCrawlGame.languagePack
            .getTutorialString("Potion Panel Tip");

    public static final String[] MSG = tutorialStrings.TEXT;
    public static final String[] LABEL = tutorialStrings.LABEL;
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("PotionPopUp");
    public static final String[] TEXT = uiStrings.TEXT;

    private int slot;

    private AbstractPotion potion;
    public boolean isHidden = true;
    public boolean targetMode = false;
    private Hitbox hbTop;
    private Hitbox hbBot;
    private Color topHoverColor = new Color(0.5F, 0.9F, 1.0F, 0.0F);
    private Color botHoverColor = new Color(1.0F, 0.4F, 0.3F, 0.0F);

    private float x;
    private float y;
    private static final int SEGMENTS = 20;
    private Vector2[] points = new Vector2[20];
    private Vector2 controlPoint;
    private float arrowScale;
    private float arrowScaleTimer = 0.0F;
    private static final float ARROW_TARGET_SCALE = 1.2F;
    private static final int TARGET_ARROW_W = 256;
    private AbstractMonster hoveredMonster = null;
    private boolean autoTargetFirst = false;

    public PotionPopUp() {
        this.hbTop = new Hitbox(286.0F * Settings.scale, 120.0F * Settings.scale);
        this.hbBot = new Hitbox(286.0F * Settings.scale, 90.0F * Settings.scale);

        for (int i = 0; i < this.points.length; i++) {
            this.points[i] = new Vector2();
        }
    }

    public void open(int slot, AbstractPotion potion) {
        this.topHoverColor.a = 0.0F;
        this.botHoverColor.a = 0.0F;
        AbstractDungeon.topPanel.selectPotionMode = false;
        this.slot = slot;
        this.potion = potion;
        this.x = potion.posX;
        this.y = Settings.HEIGHT - 230.0F * Settings.scale;
        this.isHidden = false;

        this.hbTop.move(this.x, this.y + 44.0F * Settings.scale);
        this.hbBot.move(this.x, this.y - 76.0F * Settings.scale);

        this.hbTop.clickStarted = false;
        this.hbBot.clickStarted = false;
        this.hbTop.clicked = false;
        this.hbBot.clicked = false;
    }

    public void close() {
        this.isHidden = true;
    }

    public void update() {
        if (!this.isHidden) {
            updateControllerInput();
            this.hbTop.update();
            this.hbBot.update();
            updateInput();
            if (this.potion != null) {
                TipHelper.queuePowerTips(this.x + 180.0F * Settings.scale, this.y + 70.0F * Settings.scale,
                        this.potion.tips);
            }
        } else if (this.targetMode) {
            updateControllerTargetInput();
            updateTargetMode();
        }
    }

    private void updateControllerTargetInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        int offsetEnemyIndex = 0;

        if (this.autoTargetFirst) {
            this.autoTargetFirst = false;
            offsetEnemyIndex++;
        }

        if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            offsetEnemyIndex--;
        }
        if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            offsetEnemyIndex++;
        }

        if (offsetEnemyIndex != 0) {
            AbstractMonster newTarget;

            ArrayList<AbstractMonster> prefiltered = (AbstractDungeon.getCurrRoom()).monsters.monsters;

            ArrayList<AbstractMonster> sortedMonsters = new ArrayList<>(
                    (AbstractDungeon.getCurrRoom()).monsters.monsters);

            for (AbstractMonster mons : prefiltered) {
                if (mons.isDying) {
                    sortedMonsters.remove(mons);
                }
            }

            sortedMonsters.sort(AbstractMonster.sortByHitbox);

            if (sortedMonsters.isEmpty()) {
                return;
            }

            for (AbstractMonster m : sortedMonsters) {
                if (m.hb.hovered) {
                    this.hoveredMonster = m;

                    break;
                }
            }

            if (this.hoveredMonster == null) {

                if (offsetEnemyIndex == 1) {

                    newTarget = sortedMonsters.get(0);
                } else {

                    newTarget = sortedMonsters.get(sortedMonsters.size() - 1);
                }
            } else {

                int currentTargetIndex = sortedMonsters.indexOf(this.hoveredMonster);
                int newTargetIndex = currentTargetIndex + offsetEnemyIndex;
                newTargetIndex = (newTargetIndex + sortedMonsters.size()) % sortedMonsters.size();
                newTarget = sortedMonsters.get(newTargetIndex);
            }

            if (newTarget != null) {
                Hitbox target = newTarget.hb;
                Gdx.input.setCursorPosition((int) target.cX, Settings.HEIGHT - (int) target.cY);
                this.hoveredMonster = newTarget;
            }

            if (this.hoveredMonster.halfDead) {
                this.hoveredMonster = null;
            }
        }
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        if (CInputActionSet.cancel.isJustPressed()) {
            CInputActionSet.cancel.unpress();
            close();

            return;
        }
        if (!this.hbTop.hovered && !this.hbBot.hovered) {
            if (this.potion.canUse()) {
                Gdx.input.setCursorPosition((int) this.hbTop.cX, Settings.HEIGHT - (int) this.hbTop.cY);
            } else {
                Gdx.input.setCursorPosition((int) this.hbBot.cX, Settings.HEIGHT - (int) this.hbBot.cY);
            }
        } else if (this.hbTop.hovered) {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.down.isJustPressed() || CInputActionSet.altUp
                    .isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                Gdx.input.setCursorPosition((int) this.hbBot.cX, Settings.HEIGHT - (int) this.hbBot.cY);
            }
        } else if (this.hbBot.hovered &&
                this.potion.canUse()
                && (CInputActionSet.up.isJustPressed() || CInputActionSet.down.isJustPressed() || CInputActionSet.altUp
                        .isJustPressed() || CInputActionSet.altDown.isJustPressed())) {
            Gdx.input.setCursorPosition((int) this.hbTop.cX, Settings.HEIGHT - (int) this.hbTop.cY);
        }
    }

    private void updateTargetMode() {
        if (InputHelper.justClickedRight || AbstractDungeon.isScreenUp
                || InputHelper.mY > Settings.HEIGHT - 80.0F * Settings.scale
                || AbstractDungeon.player.hoveredCard != null || InputHelper.mY < 140.0F * Settings.scale
                || CInputActionSet.cancel

                        .isJustPressed()) {
            CInputActionSet.cancel.unpress();
            this.targetMode = false;
            GameCursor.hidden = false;
        }

        this.hoveredMonster = null;
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (m.hb.hovered && !m.isDying) {
                this.hoveredMonster = m;

                break;
            }
        }
        if (InputHelper.justClickedLeft || CInputActionSet.select.isJustPressed()) {
            InputHelper.justClickedLeft = false;
            CInputActionSet.select.unpress();

            if (this.hoveredMonster != null) {
                if (AbstractDungeon.player.hasPower("Surrounded")) {
                    AbstractDungeon.player.flipHorizontal = (this.hoveredMonster.drawX < AbstractDungeon.player.drawX);
                }
                CardCrawlGame.metricData.potions_floor_usage.add(Integer.valueOf(AbstractDungeon.floorNum));
                this.potion.use((AbstractCreature) this.hoveredMonster);
                if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {

                    AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new HandCheckAction());
                }
                for (AbstractRelic r : AbstractDungeon.player.relics) {
                    r.onUsePotion();
                }
                AbstractDungeon.topPanel.destroyPotion(this.slot);
                this.targetMode = false;
                GameCursor.hidden = false;
            }
        }
    }

    private void updateInput() {
        if (InputHelper.justClickedLeft) {
            InputHelper.justClickedLeft = false;
            if (this.hbTop.hovered) {
                this.hbTop.clickStarted = true;
            } else if (this.hbBot.hovered) {
                this.hbBot.clickStarted = true;
            } else {
                close();
            }
        }

        if ((this.hbTop.clicked || (this.hbTop.hovered && CInputActionSet.select.isJustPressed()))
                && (!AbstractDungeon.isScreenUp || this.potion
                        .canUse() == true)) {
            CInputActionSet.select.unpress();
            this.hbTop.clicked = false;
            if (this.potion.canUse()) {
                if (!this.potion.targetRequired) {
                    CardCrawlGame.metricData.potions_floor_usage.add(Integer.valueOf(AbstractDungeon.floorNum));
                    this.potion.use(null);
                    for (AbstractRelic r : AbstractDungeon.player.relics) {
                        r.onUsePotion();
                    }
                    CardCrawlGame.sound.play("POTION_1");
                    AbstractDungeon.topPanel.destroyPotion(this.slot);
                } else {
                    this.targetMode = true;
                    GameCursor.hidden = true;
                    this.autoTargetFirst = true;
                }
                close();
            } else if (this.potion.ID == "SmokeBomb" &&
                    (AbstractDungeon.getCurrRoom()).monsters != null) {
                for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                    if (m.hasPower("BackAttack")) {
                        AbstractDungeon.effectList.add(new ThoughtBubble(AbstractDungeon.player.dialogX,
                                AbstractDungeon.player.dialogY, 3.0F, SmokeBomb.potionStrings.DESCRIPTIONS[1], true));

                    }

                }

            }

        } else if ((this.hbBot.clicked || (this.hbBot.hovered && CInputActionSet.select.isJustPressed())) &&
                this.potion.canDiscard()) {
            CInputActionSet.select.unpress();
            this.hbBot.clicked = false;
            CardCrawlGame.sound.play("POTION_DROP_2");
            AbstractDungeon.topPanel.destroyPotion(this.slot);
            this.slot = -1;
            this.potion = null;
            close();
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isHidden) {
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.POTION_UI_BG, this.x - 200.0F, this.y - 169.0F, 200.0F, 169.0F, 400.0F, 338.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 400, 338, false, false);

            if (this.hbTop.hovered) {
                this.topHoverColor.a = 0.5F;
            } else {
                this.topHoverColor.a = MathHelper.fadeLerpSnap(this.topHoverColor.a, 0.0F);
            }

            if (this.hbBot.hovered) {
                this.botHoverColor.a = 0.5F;
            } else {
                this.botHoverColor.a = MathHelper.fadeLerpSnap(this.botHoverColor.a, 0.0F);
            }

            sb.setBlendFunction(770, 1);
            sb.setColor(this.topHoverColor);
            sb.draw(ImageMaster.POTION_UI_TOP, this.x - 200.0F, this.y - 169.0F, 200.0F, 169.0F, 400.0F, 338.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 400, 338, false, false);

            sb.setColor(this.botHoverColor);
            sb.draw(ImageMaster.POTION_UI_BOT, this.x - 200.0F, this.y - 169.0F, 200.0F, 169.0F, 400.0F, 338.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 400, 338, false, false);

            sb.setBlendFunction(770, 771);

            Color c = Settings.CREAM_COLOR;
            if (!this.potion.canUse() || AbstractDungeon.isScreenUp) {
                c = Color.GRAY;
            }

            if (this.potion.canUse()) {
                if ((AbstractDungeon.getCurrRoom()).event != null) {
                    if (!((AbstractDungeon
                            .getCurrRoom()).event instanceof com.megacrit.cardcrawl.events.shrines.WeMeetAgain)) {
                        c = Settings.CREAM_COLOR;
                    }
                } else {
                    c = Settings.CREAM_COLOR;
                }
            }

            String label = TEXT[1];
            if (this.potion.isThrown) {
                label = TEXT[0];
            }

            FontHelper.renderFontCenteredWidth(sb, FontHelper.buttonLabelFont, label, this.x,
                    this.hbTop.cY + 4.0F * Settings.scale, c);

            FontHelper.renderFontCenteredWidth(sb, FontHelper.buttonLabelFont, TEXT[2], this.x,
                    this.hbBot.cY + 12.0F * Settings.scale, Settings.RED_TEXT_COLOR);

            this.hbTop.render(sb);
            this.hbBot.render(sb);

            if (this.hbTop.hovered) {
                if (this.potion.isThrown) {
                    TipHelper.renderGenericTip(this.x + 174.0F * Settings.scale, this.y + 20.0F * Settings.scale,
                            LABEL[0], MSG[0]);
                } else {
                    TipHelper.renderGenericTip(this.x + 174.0F * Settings.scale, this.y + 20.0F * Settings.scale,
                            LABEL[1], MSG[1]);
                }
            } else if (this.hbBot.hovered) {
                TipHelper.renderGenericTip(this.x + 174.0F * Settings.scale, this.y + 20.0F * Settings.scale, LABEL[2],
                        MSG[2]);
            }
        }

        if (this.targetMode) {
            if (this.hoveredMonster != null) {
                this.hoveredMonster.renderReticle(sb);
            }
            renderTargetingUi(sb);
        }
    }

    private void renderTargetingUi(SpriteBatch sb) {
        float x = InputHelper.mX, y = InputHelper.mY;
        this.controlPoint = new Vector2(this.potion.posX - (x - this.potion.posX) / 4.0F,
                y + (y - this.potion.posY - 40.0F * Settings.scale) / 2.0F);

        if (this.hoveredMonster == null) {
            this.arrowScale = Settings.scale;
            this.arrowScaleTimer = 0.0F;
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 1.0F));
        } else {
            this.arrowScaleTimer += Gdx.graphics.getDeltaTime();
            if (this.arrowScaleTimer > 1.0F) {
                this.arrowScaleTimer = 1.0F;
            }

            this.arrowScale = Interpolation.elasticOut.apply(Settings.scale, Settings.scale * 1.2F,
                    this.arrowScaleTimer);

            sb.setColor(new Color(1.0F, 0.2F, 0.3F, 1.0F));
        }

        Vector2 tmp = new Vector2(this.controlPoint.x - x, this.controlPoint.y - y);
        tmp.nor();

        drawCurvedLine(sb, new Vector2(this.potion.posX, this.potion.posY - 40.0F * Settings.scale), new Vector2(x, y),
                this.controlPoint);

        sb.draw(ImageMaster.TARGET_UI_ARROW, x - 128.0F, y - 128.0F, 128.0F, 128.0F, 256.0F, 256.0F, this.arrowScale,
                this.arrowScale, tmp

                        .angle() + 90.0F,
                0, 0, 256, 256, false, false);
    }

    private void drawCurvedLine(SpriteBatch sb, Vector2 start, Vector2 end, Vector2 control) {
        float radius = 7.0F * Settings.scale;

        for (int i = 0; i < this.points.length - 1; i++) {
            float angle;
            this.points[i] = (Vector2) Bezier.quadratic((Vector) this.points[i], i / 20.0F, (Vector) start,
                    (Vector) control, (Vector) end, (Vector) new Vector2());
            radius += 0.4F * Settings.scale;

            if (i != 0) {
                Vector2 tmp = new Vector2((this.points[i - 1]).x - (this.points[i]).x,
                        (this.points[i - 1]).y - (this.points[i]).y);
                angle = tmp.nor().angle() + 90.0F;
            } else {
                Vector2 tmp = new Vector2(this.controlPoint.x - (this.points[i]).x,
                        this.controlPoint.y - (this.points[i]).y);
                angle = tmp.nor().angle() + 270.0F;
            }

            sb.draw(ImageMaster.TARGET_UI_CIRCLE, (this.points[i]).x - 64.0F, (this.points[i]).y - 64.0F, 64.0F, 64.0F,
                    128.0F, 128.0F, radius / 18.0F, radius / 18.0F, angle, 0, 0, 128, 128, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\panels\
 * PotionPopUp.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
