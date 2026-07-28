package com.megacrit.cardcrawl.ui.panels;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.screens.options.ConfirmPopup;
import com.megacrit.cardcrawl.vfx.WarningSignEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class DeleteSaveConfirmPopup
        extends ConfirmPopup {
    protected static final String[] D_TEXT = (CardCrawlGame.languagePack.getUIString("DeletePopup")).TEXT;

    private ArrayList<WarningSignEffect> effects = new ArrayList<>();

    public DeleteSaveConfirmPopup() {
        super(D_TEXT[0], D_TEXT[3], ConfirmType.DELETE_SAVE);
    }

    public void update() {
        super.update();

        if (this.shown && this.effects.isEmpty()) {
            this.effects.add(new WarningSignEffect(Settings.WIDTH / 2.0F, Settings.OPTION_Y + 275.0F * Settings.scale));
        }

        for (Iterator<WarningSignEffect> i = this.effects.iterator(); i.hasNext();) {
            WarningSignEffect e = i.next();
            e.update();
            if (e.isDone) {
                i.remove();
            }
        }
    }

    public void open(int slot) {
        this.slot = slot;
        this.shown = true;
    }

    public void render(SpriteBatch sb) {
        super.render(sb);
        renderWarning(sb);
    }

    private void renderWarning(SpriteBatch sb) {
        for (WarningSignEffect e : this.effects)
            e.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\panels\
 * DeleteSaveConfirmPopup.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */
