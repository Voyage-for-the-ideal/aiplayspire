package com.megacrit.cardcrawl.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;

public class BottomBgPanel {
    private static final float SNAP_THRESHOLD = 0.3F;
    private static final float LERP_SPEED = 7.0F;
    private float normal_y = 72.0F * Settings.scale;
    private float current_y;
    private float target_y;
    private float hide_y = 0.0F;
    private float overlay_y = Settings.HEIGHT * 0.5F;
    public boolean doneAnimating = true;

    public BottomBgPanel() {
        this.current_y = this.normal_y;
        this.target_y = this.current_y;
    }

    public void changeMode(Mode mode) {
        switch (mode) {
            case NORMAL:
                this.target_y = this.normal_y;
                this.doneAnimating = false;
                break;
            case OVERLAY:
                this.target_y = this.overlay_y;
                this.doneAnimating = false;
                break;
            case HIDDEN:
                this.target_y = this.hide_y;
                this.doneAnimating = false;
                break;
        }
    }

    public void updatePositions() {
        if (this.current_y != this.target_y) {
            this.current_y = MathUtils.lerp(this.current_y, this.target_y, Gdx.graphics.getDeltaTime() * 7.0F);
            if (Math.abs(this.current_y - this.target_y) < 0.3F) {
                this.current_y = this.target_y;
                this.doneAnimating = true;
            } else {
                this.doneAnimating = false;
            }
        }
    }

    public enum Mode {
        NORMAL, OVERLAY, HIDDEN;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\panels\
 * BottomBgPanel.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

