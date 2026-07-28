package com.megacrit.cardcrawl.orbs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.OrbStrings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.OrbFlareEffect;
import com.megacrit.cardcrawl.vfx.combat.PlasmaOrbActivateEffect;
import com.megacrit.cardcrawl.vfx.combat.PlasmaOrbPassiveEffect;

public class Plasma extends AbstractOrb {
    public static final String ORB_ID = "Plasma";
    private static final OrbStrings orbString = CardCrawlGame.languagePack.getOrbString("Plasma");
    public static final String[] DESC = orbString.DESCRIPTION;
    private float vfxTimer = 1.0F;
    private float vfxIntervalMin = 0.1F;
    private float vfxIntervalMax = 0.4F;

    private static final float ORB_WAVY_DIST = 0.04F;

    private static final float PI_4 = 12.566371F;

    public Plasma() {
        this.ID = "Plasma";
        this.img = ImageMaster.ORB_PLASMA;
        this.name = orbString.NAME;
        this.baseEvokeAmount = 2;
        this.evokeAmount = this.baseEvokeAmount;
        this.basePassiveAmount = 1;
        this.passiveAmount = this.basePassiveAmount;
        updateDescription();
        this.angle = MathUtils.random(360.0F);
        this.channelAnimTimer = 0.5F;
    }

    public void updateDescription() {
        applyFocus();
        this.description = DESC[0] + this.evokeAmount + DESC[1];
    }

    public void onEvoke() {
        AbstractDungeon.actionManager.addToTop((AbstractGameAction) new GainEnergyAction(this.evokeAmount));
    }

    public void onStartOfTurn() {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new VFXAction(
                (AbstractGameEffect) new OrbFlareEffect(this, OrbFlareEffect.OrbFlareColor.PLASMA), 0.1F));
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction) new GainEnergyAction(this.passiveAmount));
    }

    public void triggerEvokeAnimation() {
        CardCrawlGame.sound.play("ORB_PLASMA_EVOKE", 0.1F);
        AbstractDungeon.effectsQueue.add(new PlasmaOrbActivateEffect(this.cX, this.cY));
    }

    public void updateAnimation() {
        super.updateAnimation();
        this.angle += Gdx.graphics.getDeltaTime() * 45.0F;

        this.vfxTimer -= Gdx.graphics.getDeltaTime();
        if (this.vfxTimer < 0.0F) {
            AbstractDungeon.effectList.add(new PlasmaOrbPassiveEffect(this.cX, this.cY));
            this.vfxTimer = MathUtils.random(this.vfxIntervalMin, this.vfxIntervalMax);
        }
    }

    public void render(SpriteBatch sb) {
        this.c.a /= 2.0F;
        sb.setColor(this.shineColor);
        sb.draw(this.img, this.cX - 48.0F, this.cY - 48.0F + this.bobEffect.y, 48.0F, 48.0F, 96.0F, 96.0F, this.scale +

                MathUtils.sin(this.angle / 12.566371F) * 0.04F * Settings.scale, this.scale, this.angle, 0, 0, 96, 96,
                false, false);

        sb.setBlendFunction(770, 1);
        sb.draw(this.img, this.cX - 48.0F, this.cY - 48.0F + this.bobEffect.y, 48.0F, 48.0F, 96.0F, 96.0F, this.scale,
                this.scale +

                        MathUtils.sin(this.angle / 12.566371F) * 0.04F * Settings.scale,
                -this.angle, 0, 0, 96, 96, false, false);

        sb.setBlendFunction(770, 771);
        renderText(sb);
        this.hb.render(sb);
    }

    protected void renderText(SpriteBatch sb) {
        if (this.showEvokeValue) {
            FontHelper.renderFontCentered(sb, FontHelper.cardEnergyFont_L,

                    Integer.toString(this.evokeAmount), this.cX + NUM_X_OFFSET,
                    this.cY + this.bobEffect.y / 2.0F + NUM_Y_OFFSET - 4.0F * Settings.scale,
                    new Color(0.2F, 1.0F, 1.0F, this.c.a), this.fontScale);
        }
    }

    public void playChannelSFX() {
        CardCrawlGame.sound.play("ORB_PLASMA_CHANNEL", 0.1F);
    }

    public AbstractOrb makeCopy() {
        return new Plasma();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\orbs\Plasma.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

