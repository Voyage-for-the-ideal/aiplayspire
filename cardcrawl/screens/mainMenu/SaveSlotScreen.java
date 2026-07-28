package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen;
import com.megacrit.cardcrawl.ui.panels.DeleteSaveConfirmPopup;
import com.megacrit.cardcrawl.ui.panels.RenamePopup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class SaveSlotScreen {
    private static final Logger logger = LogManager.getLogger(SaveSlotScreen.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("SaveSlotScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    public static boolean slotDeleted = false;
    private Color screenColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    public Color uiColor = new Color(1.0F, 0.965F, 0.886F, 0.0F);
    public boolean shown = false;
    public ArrayList<SaveSlot> slots = new ArrayList<>();
    public MenuCancelButton cancelButton = new MenuCancelButton();

    private RenamePopup renamePopup = new RenamePopup();
    private DeleteSaveConfirmPopup deletePopup = new DeleteSaveConfirmPopup();
    public CurrentPopup curPop = CurrentPopup.NONE;

    public enum CurrentPopup {
        DELETE, RENAME, NONE;
    }

    public void update() {
        this.deletePopup.update();
        this.renamePopup.update();

        updateColors();

        switch (this.curPop) {

            case NONE:
                if (this.shown) {
                    updateSaveSlots();
                    updateControllerInput();
                }

                updateCancelButton();
                break;
        }
    }

    private void updateCancelButton() {
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || (!this.cancelButton.isHidden && InputActionSet.cancel.isJustPressed())) {
            this.cancelButton.hb.clicked = false;
            if (!((SaveSlot) this.slots.get(CardCrawlGame.saveSlot)).emptySlot) {
                confirm(CardCrawlGame.saveSlot);
            } else {
                for (int i = 0; i < 3; i++) {
                    if (!((SaveSlot) this.slots.get(i)).emptySlot) {
                        confirm(i);
                    }
                }
            }
        }
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode || this.slots.isEmpty()) {
            return;
        }

        boolean anyHovered = false;

        int index = 0;
        for (SaveSlot slot : this.slots) {
            if (slot.slotHb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            CInputHelper.setCursor(((SaveSlot) this.slots.get(0)).slotHb);
        } else {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                index--;
                if (index < 0) {
                    index = 2;
                }
            } else {
                index++;
                if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) && index > 2) {
                    index = 0;
                }
            }

            CInputHelper.setCursor(((SaveSlot) this.slots.get(index)).slotHb);
        }
    }

    private void updateColors() {
        if (this.shown) {
            this.screenColor.a = MathHelper.fadeLerpSnap(this.screenColor.a, 0.75F);
            this.uiColor.a = MathHelper.fadeLerpSnap(this.uiColor.a, 1.0F);
        } else {
            this.screenColor.a = MathHelper.fadeLerpSnap(this.screenColor.a, 0.0F);
            this.uiColor.a = MathHelper.fadeLerpSnap(this.uiColor.a, 0.0F);
        }
    }

    private void updateSaveSlots() {
        for (SaveSlot slot : this.slots) {
            slot.update();
        }
    }

    public void open(String curName) {
        if (this.slots.isEmpty()) {
            this.slots.add(new SaveSlot(0));
            this.slots.add(new SaveSlot(1));
            this.slots.add(new SaveSlot(2));
            SaveSlot.uiColor = this.uiColor;
        }

        this.shown = true;
        for (SaveSlot s : this.slots) {
            if (!s.emptySlot) {
                this.cancelButton.show(CharacterSelectScreen.TEXT[5]);
                break;
            }
        }
    }

    public void rename(int slot, String name) {
        ((SaveSlot) this.slots.get(slot)).setName(name);
    }

    public void confirm(int slot) {
        this.shown = false;
        CardCrawlGame.saveSlot = slot;
        CardCrawlGame.playerName = ((SaveSlot) this.slots.get(slot)).getName();
        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
        this.cancelButton.hide();

        if (CardCrawlGame.saveSlotPref.getInteger("DEFAULT_SLOT", -1) != slot || slotDeleted) {
            logger.info("Default slot updated: " + slot);
            CardCrawlGame.saveSlotPref.putInteger("DEFAULT_SLOT", slot);
            CardCrawlGame.reloadPrefs();
            CardCrawlGame.saveSlotPref.flush();
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.screenColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        if (this.shown) {
            renderSaveSlots(sb);
            this.deletePopup.render(sb);
            this.renamePopup.render(sb);
            if (this.curPop == CurrentPopup.NONE) {
                this.cancelButton.render(sb);
                renderSelectSlotMessage(sb);
            }
        }
    }

    private void renderSelectSlotMessage(SpriteBatch sb) {
        boolean showingTip = false;
        for (SaveSlot s : this.slots) {
            if (s.renameHb.hovered) {
                FontHelper.renderFontCentered(sb, FontHelper.topPanelAmountFont, TEXT[1], Settings.WIDTH / 2.0F,
                        80.0F * Settings.scale, Settings.BLUE_TEXT_COLOR);

                showingTip = true;

                break;
            }
            if (s.deleteHb.hovered) {
                FontHelper.renderFontCentered(sb, FontHelper.topPanelAmountFont, TEXT[2], Settings.WIDTH / 2.0F,
                        80.0F * Settings.scale, Settings.RED_TEXT_COLOR);

                showingTip = true;

                break;
            }
        }
        if (!showingTip) {
            FontHelper.renderFontCentered(sb, FontHelper.topPanelAmountFont, TEXT[0], Settings.WIDTH / 2.0F,
                    80.0F * Settings.scale, Settings.CREAM_COLOR);
        }

        if (Settings.isControllerMode) {
            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.select
                    .getKeyImg(),
                    Settings.WIDTH / 2.0F -
                            FontHelper.getSmartWidth(FontHelper.topPanelAmountFont, TEXT[0], 99999.0F, 0.0F) / 2.0F
                            - 32.0F - 48.0F * Settings.scale,
                    80.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0,
                    0, 64, 64, false, false);
        }
    }

    private void renderSaveSlots(SpriteBatch sb) {
        sb.setColor(this.uiColor);
        for (SaveSlot slot : this.slots) {
            slot.render(sb);
        }
    }

    public void openDeletePopup(int index) {
        this.deletePopup.open(index);
        this.curPop = CurrentPopup.DELETE;
    }

    public void deleteSlot(int index) {
        CardCrawlGame.saveSlotPref.putString(SaveHelper.slotName("PROFILE_NAME", index), "");
        ((SaveSlot) this.slots.get(index)).clear();
    }

    public void openRenamePopup(int index, boolean newSave) {
        this.renamePopup.open(index, newSave);
        this.curPop = CurrentPopup.RENAME;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\mainMenu\
 * SaveSlotScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

