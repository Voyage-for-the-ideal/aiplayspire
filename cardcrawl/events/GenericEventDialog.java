package com.megacrit.cardcrawl.events;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.DialogWord;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

import java.util.ArrayList;
import java.util.Scanner;

public class GenericEventDialog {
    private Texture img = null;
    private static final float DIALOG_MSG_X_IMAGE = 816.0F * Settings.xScale;
    private static final float DIALOG_MSG_W_IMAGE = 900.0F * Settings.scale;

    private Color panelColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private Color titleColor = new Color(1.0F, 0.835F, 0.39F, 0.0F);
    private Color borderColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private Color imgColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private float animateTimer = 0.0F;

    private static final float ANIM_SPEED = 0.5F;

    private static boolean show = false;
    private String title = "";
    private static final float TITLE_X = 570.0F * Settings.xScale;
    private static final float TITLE_Y = Settings.EVENT_Y + 408.0F * Settings.scale;

    private static float curLineWidth = 0.0F;
    private static int curLine = 0;
    private static DialogWord.AppearEffect a_effect;
    private static Scanner s;
    private static GlyphLayout gl = new GlyphLayout();
    private static ArrayList<DialogWord> words = new ArrayList<>();
    private static boolean textDone = true;
    private static float wordTimer = 0.0F;
    private static final float WORD_TIME = 0.02F;
    private static final float CHAR_SPACING = 8.0F * Settings.scale;
    private static final float LINE_SPACING = Settings.BIG_TEXT_MODE ? (40.0F * Settings.scale)
            : (38.0F * Settings.scale);
    private static final float DIALOG_MSG_X_TEXT = 455.0F * Settings.xScale;
    private static final float DIALOG_MSG_Y_TEXT = Settings.isMobile ? (Settings.EVENT_Y + 330.0F * Settings.scale)
            : (Settings.EVENT_Y + 300.0F * Settings.scale);

    private static final float DIALOG_MSG_W_TEXT = 1000.0F * Settings.scale;
    private static float DIALOG_MSG_X = DIALOG_MSG_X_TEXT;
    private static float DIALOG_MSG_Y = DIALOG_MSG_Y_TEXT;
    private static float DIALOG_MSG_W = DIALOG_MSG_W_TEXT;

    public ArrayList<LargeDialogOptionButton> optionList = new ArrayList<>();
    public static int selectedOption = -1;
    public static boolean waitForInput = true;

    public void loadImage(String imgUrl) {
        if (this.img != null) {
            this.img.dispose();
            this.img = null;
        }
        this.img = ImageMaster.loadImage(imgUrl);

        DIALOG_MSG_X = DIALOG_MSG_X_IMAGE;
        DIALOG_MSG_W = DIALOG_MSG_W_IMAGE;
    }

    private void clearImage() {
        dispose();
        DIALOG_MSG_X = DIALOG_MSG_X_TEXT;
        DIALOG_MSG_Y = DIALOG_MSG_Y_TEXT;
        DIALOG_MSG_W = DIALOG_MSG_W_TEXT;
    }

    public void update() {
        animateIn();

        if (show && this.animateTimer == 0.0F) {
            for (int i = 0; i < this.optionList.size(); i++) {
                ((LargeDialogOptionButton) this.optionList.get(i)).update(this.optionList.size());
                if (((LargeDialogOptionButton) this.optionList.get(i)).pressed && waitForInput) {
                    selectedOption = i;
                    ((LargeDialogOptionButton) this.optionList.get(i)).pressed = false;
                    waitForInput = false;
                }
            }
        }

        if (!Settings.lineBreakViaCharacter) {
            bodyTextEffect();
        } else {
            bodyTextEffectCN();
        }

        for (DialogWord w : words) {
            w.update();
        }
    }

    private void animateIn() {
        if (show) {
            this.animateTimer -= Gdx.graphics.getDeltaTime();
            if (this.animateTimer < 0.0F) {
                this.animateTimer = 0.0F;
            }
            this.panelColor.a = MathHelper.slowColorLerpSnap(this.panelColor.a, 1.0F);

            if (this.panelColor.a > 0.8F) {
                this.titleColor.a = MathHelper.slowColorLerpSnap(this.titleColor.a, 1.0F);
                this.borderColor.a = this.titleColor.a;
                if (this.borderColor.a > 0.8F) {
                    this.imgColor.a = MathHelper.slowColorLerpSnap(this.imgColor.a, 1.0F);
                }
            }
        }
    }

    public static int getSelectedOption() {
        waitForInput = true;
        return selectedOption;
    }

    public static void hide() {
        show = false;
    }

    public static void show() {
        show = true;
    }

    public void clear() {
        show = false;
        clearImage();
        this.animateTimer = 0.0F;
        this.panelColor.a = 0.0F;
        this.titleColor.a = 0.0F;
        this.imgColor.a = 0.0F;
        this.borderColor.a = 0.0F;
        this.optionList.clear();
        words.clear();
        waitForInput = true;
    }

    public static void cleanUp() {
        words.clear();
        show = false;
        waitForInput = true;
    }

    public void show(String title, String text) {
        this.title = title;
        updateBodyText(text);
        if (Settings.FAST_MODE) {
            this.animateTimer = 0.125F;
        } else {
            this.animateTimer = 0.5F;
        }
        show = true;
    }

    public void clearAllDialogs() {
        this.optionList.clear();
    }

    public void removeDialogOption(int slot) {
        if (slot < this.optionList.size()) {
            this.optionList.remove(slot);
        }
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void clearRemainingOptions() {
        for (int i = this.optionList.size() - 1; i > 0; i--) {
            this.optionList.remove(i);
        }
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, AbstractCard previewCard) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, previewCard));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, AbstractRelic previewRelic) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, previewRelic));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, AbstractCard previewCard, AbstractRelic previewRelic) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, previewCard, previewRelic));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, boolean isDisabled) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, isDisabled));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, boolean isDisabled, AbstractCard previewCard) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, isDisabled, previewCard));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, boolean isDisabled, AbstractRelic previewRelic) {
        this.optionList.add(new LargeDialogOptionButton(this.optionList.size(), text, isDisabled, previewRelic));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void setDialogOption(String text, boolean isDisabled, AbstractCard previewCard, AbstractRelic previewRelic) {
        this.optionList
                .add(new LargeDialogOptionButton(this.optionList.size(), text, isDisabled, previewCard, previewRelic));
        for (LargeDialogOptionButton b : this.optionList) {
            b.calculateY(this.optionList.size());
        }
    }

    public void updateDialogOption(int slot, String text) {
        if (!this.optionList.isEmpty()) {
            if (this.optionList.size() > slot) {
                this.optionList.set(slot, new LargeDialogOptionButton(slot, text));
            } else {
                this.optionList.add(new LargeDialogOptionButton(slot, text));
            }
        } else {
            this.optionList.add(new LargeDialogOptionButton(slot, text));
        }
    }

    public void updateDialogOption(int slot, String text, boolean isDisabled) {
        if (!this.optionList.isEmpty()) {
            if (this.optionList.size() > slot) {
                this.optionList.set(slot, new LargeDialogOptionButton(slot, text, isDisabled));
            } else {
                this.optionList.add(new LargeDialogOptionButton(slot, text, isDisabled));
            }
        } else {
            this.optionList.add(new LargeDialogOptionButton(slot, text, isDisabled));
        }
    }

    public void updateDialogOption(int slot, String text, AbstractCard previewCard) {
        if (!this.optionList.isEmpty()) {
            if (this.optionList.size() > slot) {
                this.optionList.set(slot, new LargeDialogOptionButton(slot, text, previewCard));
            } else {
                this.optionList.add(new LargeDialogOptionButton(slot, text, previewCard));
            }
        } else {
            this.optionList.add(new LargeDialogOptionButton(slot, text, previewCard));
        }
    }

    public void updateBodyText(String text) {
        updateBodyText(text, DialogWord.AppearEffect.BUMP_IN);
    }

    public void updateBodyText(String text, DialogWord.AppearEffect ae) {
        s = new Scanner(text);
        words.clear();
        textDone = false;
        a_effect = ae;
        curLineWidth = 0.0F;
        curLine = 0;
    }

    private void bodyTextEffectCN() {
        wordTimer -= Gdx.graphics.getDeltaTime();
        if (wordTimer < 0.0F && !textDone) {
            if (Settings.FAST_MODE) {
                wordTimer = 0.005F;
            } else {
                wordTimer = 0.02F;
            }

            if (s.hasNext()) {
                String word = s.next();

                if (word.equals("NL")) {
                    curLine++;
                    curLineWidth = 0.0F;

                    return;
                }
                DialogWord.WordColor color = DialogWord.identifyWordColor(word);
                if (color != DialogWord.WordColor.DEFAULT) {
                    word = word.substring(2, word.length());
                }

                DialogWord.WordEffect effect = DialogWord.identifyWordEffect(word);
                if (effect != DialogWord.WordEffect.NONE) {
                    word = word.substring(1, word.length() - 1);
                }

                for (int i = 0; i < word.length(); i++) {
                    String tmp = Character.toString(word.charAt(i));

                    gl.setText(FontHelper.charDescFont, tmp);
                    if (curLineWidth + gl.width > DIALOG_MSG_W) {
                        curLine++;
                        curLineWidth = gl.width;
                    } else {
                        curLineWidth += gl.width;
                    }

                    words.add(new DialogWord(FontHelper.charDescFont, tmp, a_effect, effect, color,
                            DIALOG_MSG_X + curLineWidth - gl.width, DIALOG_MSG_Y - LINE_SPACING * curLine, curLine));

                    if (!show) {
                        ((DialogWord) words.get(words.size() - 1)).dialogFadeOut();
                    }
                }
            } else {
                textDone = true;
                s.close();
            }
        }
    }

    private void bodyTextEffect() {
        wordTimer -= Gdx.graphics.getDeltaTime();
        if (wordTimer < 0.0F && !textDone) {
            if (Settings.FAST_MODE) {
                wordTimer = 0.005F;
            } else {
                wordTimer = 0.02F;
            }

            if (s.hasNext()) {
                String word = s.next();

                if (word.equals("NL")) {
                    curLine++;
                    curLineWidth = 0.0F;

                    return;
                }
                DialogWord.WordColor color = DialogWord.identifyWordColor(word);
                if (color != DialogWord.WordColor.DEFAULT) {
                    word = word.substring(2, word.length());
                }

                DialogWord.WordEffect effect = DialogWord.identifyWordEffect(word);
                if (effect != DialogWord.WordEffect.NONE) {
                    word = word.substring(1, word.length() - 1);
                }

                gl.setText(FontHelper.charDescFont, word);
                if (curLineWidth + gl.width > DIALOG_MSG_W) {
                    curLine++;
                    curLineWidth = gl.width + CHAR_SPACING;
                } else {
                    curLineWidth += gl.width + CHAR_SPACING;
                }

                words.add(new DialogWord(FontHelper.charDescFont, word, a_effect, effect, color,
                        DIALOG_MSG_X + curLineWidth - gl.width, DIALOG_MSG_Y - LINE_SPACING * curLine, curLine));

                if (!show) {
                    ((DialogWord) words.get(words.size() - 1)).dialogFadeOut();
                }
            } else {
                textDone = true;
                s.close();
            }
        }
    }

    public void render(SpriteBatch sb) {
        if (show && !AbstractDungeon.player.isDead) {
            sb.setColor(this.panelColor);

            sb.draw(AbstractDungeon.eventBackgroundImg, Settings.WIDTH / 2.0F - 881.5F - 12.0F * Settings.xScale,
                    Settings.EVENT_Y - 403.0F + 64.0F * Settings.scale, 881.5F, 403.0F, 1763.0F, 806.0F,
                    Settings.xScale, Settings.scale, 0.0F, 0, 0, 1763, 806, false, false);

            if (this.img != null) {
                sb.setColor(this.imgColor);
                sb.draw(this.img, 460.0F * Settings.xScale - 300.0F, Settings.EVENT_Y - 300.0F + 16.0F * Settings.scale,
                        300.0F, 300.0F, 600.0F, 600.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 600, 600, false,
                        false);

                sb.setColor(this.borderColor);
                sb.draw(ImageMaster.EVENT_IMG_FRAME, 460.0F * Settings.xScale - 305.0F,
                        Settings.EVENT_Y - 305.0F + 16.0F * Settings.scale, 305.0F, 305.0F, 610.0F, 610.0F,
                        Settings.scale, Settings.scale, 0.0F, 0, 0, 610, 610, false, false);
            }

            FontHelper.renderFontCentered(sb, FontHelper.losePowerFont, this.title, TITLE_X, TITLE_Y, this.titleColor,
                    0.88F);

            for (DialogWord w : words) {
                w.render(sb);
            }

            for (LargeDialogOptionButton b : this.optionList) {
                b.render(sb);
            }
            for (LargeDialogOptionButton b : this.optionList) {
                b.renderCardPreview(sb);
            }
            for (LargeDialogOptionButton b : this.optionList) {
                b.renderRelicPreview(sb);
            }
        }
    }

    public void dispose() {
        if (this.img != null) {
            this.img.dispose();
            this.img = null;
            show = false;
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\
 * GenericEventDialog.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

