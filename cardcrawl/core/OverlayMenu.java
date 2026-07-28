package com.megacrit.cardcrawl.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.buttons.CancelButton;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import com.megacrit.cardcrawl.ui.buttons.ProceedButton;
import com.megacrit.cardcrawl.ui.panels.*;

import java.util.ArrayList;

public class OverlayMenu {
    private AbstractPlayer player;
    public static final float HAND_HIDE_Y = 300.0F * Settings.scale;
    public boolean combatPanelsShown = true;
    public float tipHoverDuration = 0.0F;
    private static final float HOVER_TIP_TIME = 0.01F;
    public boolean hoveredTip = false;
    public ArrayList<AbstractRelic> relicQueue = new ArrayList<>();

    public BottomBgPanel bottomBgPanel = new BottomBgPanel();
    public EnergyPanel energyPanel = new EnergyPanel();
    private Color blackScreenColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private float blackScreenTarget = 0.0F;

    public DrawPilePanel combatDeckPanel = new DrawPilePanel();
    public DiscardPilePanel discardPilePanel = new DiscardPilePanel();
    public ExhaustPanel exhaustPanel = new ExhaustPanel();

    public EndTurnButton endTurnButton = new EndTurnButton();
    public ProceedButton proceedButton = new ProceedButton();
    public CancelButton cancelButton = new CancelButton();

    public OverlayMenu(AbstractPlayer player) {
        this.player = player;
    }

    public void update() {
        this.hoveredTip = false;
        this.bottomBgPanel.updatePositions();
        this.energyPanel.updatePositions();
        this.energyPanel.update();
        this.player.hand.update();
        this.combatDeckPanel.updatePositions();
        this.discardPilePanel.updatePositions();
        this.exhaustPanel.updatePositions();
        this.endTurnButton.update();
        this.proceedButton.update();
        this.cancelButton.update();
        updateBlackScreen();

        for (AbstractRelic r : AbstractDungeon.player.relics) {
            if (r != null) {
                r.update();
            }
        }

        for (AbstractBlight b : AbstractDungeon.player.blights) {
            if (b != null) {
                b.update();
            }
        }

        if (!this.relicQueue.isEmpty()) {
            for (AbstractRelic r : this.relicQueue) {
                AbstractDungeon.getCurrRoom().spawnRelicAndObtain(Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F, r);
            }
            this.relicQueue.clear();
        }

        if (this.hoveredTip) {
            this.tipHoverDuration += Gdx.graphics.getDeltaTime();
            if (this.tipHoverDuration > 0.01F) {
                this.tipHoverDuration = 0.02F;
            }
        } else {
            this.tipHoverDuration -= Gdx.graphics.getDeltaTime();
            if (this.tipHoverDuration < 0.0F) {
                this.tipHoverDuration = 0.0F;
            }
        }
    }

    public void showCombatPanels() {
        this.combatPanelsShown = true;
        this.bottomBgPanel.changeMode(BottomBgPanel.Mode.NORMAL);
        this.combatDeckPanel.show();
        this.discardPilePanel.show();
        this.exhaustPanel.show();
        this.energyPanel.show();
        this.endTurnButton.show();

        if (AbstractDungeon.ftue == null) {
            this.proceedButton.hide();
        }

        this.player.hand.refreshHandLayout();
    }

    public void hideCombatPanels() {
        this.combatPanelsShown = false;
        this.bottomBgPanel.changeMode(BottomBgPanel.Mode.HIDDEN);
        this.combatDeckPanel.hide();
        this.discardPilePanel.hide();
        this.exhaustPanel.hide();
        this.endTurnButton.hide();
        this.energyPanel.hide();

        for (AbstractCard c : this.player.hand.group) {
            c.target_y = -AbstractCard.IMG_HEIGHT;
        }
    }

    public void showBlackScreen(float target) {
        this.blackScreenTarget = target;
    }

    public void showBlackScreen() {
        if (this.blackScreenTarget < 0.85F) {
            this.blackScreenTarget = 0.85F;
        }
    }

    public void hideBlackScreen() {
        this.blackScreenTarget = 0.0F;
    }

    private void updateBlackScreen() {
        if (this.blackScreenColor.a != this.blackScreenTarget) {
            if (this.blackScreenTarget > this.blackScreenColor.a) {
                this.blackScreenColor.a += Gdx.graphics.getDeltaTime() * 2.0F;
                if (this.blackScreenColor.a > this.blackScreenTarget) {
                    this.blackScreenColor.a = this.blackScreenTarget;
                }
            } else {
                this.blackScreenColor.a -= Gdx.graphics.getDeltaTime() * 2.0F;
                if (this.blackScreenColor.a < this.blackScreenTarget) {
                    this.blackScreenColor.a = this.blackScreenTarget;
                }
            }
        }
    }

    public void render(SpriteBatch sb) {
        this.endTurnButton.render(sb);
        this.proceedButton.render(sb);
        this.cancelButton.render(sb);

        if (!Settings.hideLowerElements) {
            this.energyPanel.render(sb);
            this.combatDeckPanel.render(sb);
            this.discardPilePanel.render(sb);
            this.exhaustPanel.render(sb);
        }

        this.player.renderHand(sb);
        this.player.hand.renderTip(sb);
    }

    public void renderBlackScreen(SpriteBatch sb) {
        if (this.blackScreenColor.a != 0.0F) {
            sb.setColor(this.blackScreenColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\OverlayMenu.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

