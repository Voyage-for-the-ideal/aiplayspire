package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.InputProcessor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.ui.panels.RenamePopup;
import com.megacrit.cardcrawl.ui.panels.SeedPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TypeHelper
        implements InputProcessor {
    private static final Logger logger = LogManager.getLogger(TypeHelper.class.getName());
    private boolean seed;

    public TypeHelper() {
        this.seed = false;
    }

    public TypeHelper(boolean seed) {
        this.seed = seed;
    }

    public boolean keyDown(int keycode) {
        return false;
    }

    public boolean keyUp(int keycode) {
        return false;
    }

    public boolean keyTyped(char character) {
        String charStr = String.valueOf(character);
        logger.info(charStr);
        if (charStr.length() != 1) {
            return false;
        }

        if (this.seed) {
            if (SeedPanel.isFull()) {
                return false;
            }

            if (InputHelper.isPasteJustPressed()) {
                return false;
            }

            String converted = SeedHelper.getValidCharacter(charStr, SeedPanel.textField);
            if (converted != null) {
                SeedPanel.textField += converted;
            }
        } else {

            if (FontHelper.getSmartWidth(FontHelper.cardTitleFont, RenamePopup.textField, 1.0E7F, 0.0F, 0.82F) >= 240.0F
                    * Settings.scale) {
                return false;
            }

            if (Character.isDigit(character) || Character.isLetter(character)) {
                RenamePopup.textField += charStr;
            }
        }
        return true;
    }

    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    public boolean scrolled(int amount) {
        return false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\TypeHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

