package com.megacrit.cardcrawl.events;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.DialogWord;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

import java.util.ArrayList;
import java.util.Scanner;

public class RoomEventDialog {
    private Color color = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private Color targetColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private static final Color PANEL_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.5F);
    private static final float COLOR_FADE_SPEED = 8.0F;
    private boolean isMoving = false;
    private float animateTimer = 0.0F;

    private static final float ANIM_SPEED = 0.5F;

    private boolean show = false;
    private float curLineWidth = 0.0F;
    private int curLine = 0;
    private DialogWord.AppearEffect a_effect;
    private Scanner s;
    private GlyphLayout gl = new GlyphLayout();
    private ArrayList<DialogWord> words = new ArrayList<>();
    private boolean textDone = true;
    private float wordTimer = 0.0F;
    private static final float WORD_TIME = 0.02F;
    private static final float CHAR_SPACING = 8.0F * Settings.scale;
    private static final float LINE_SPACING = 34.0F * Settings.scale;
    private static final float DIALOG_MSG_X = Settings.WIDTH * 0.1F;
    private static final float DIALOG_MSG_Y = 250.0F * Settings.scale;
    private static final float DIALOG_MSG_W = Settings.WIDTH * 0.8F;

    public static ArrayList<LargeDialogOptionButton> optionList = new ArrayList<>();
    public static int selectedOption = -1;
    public static boolean waitForInput = true;

    public void update() {
        if (this.isMoving) {
            this.animateTimer -= Gdx.graphics.getDeltaTime();
            if (this.animateTimer < 0.0F) {
                this.animateTimer = 0.0F;
                this.isMoving = false;
            }
        }

        this.color = this.color.lerp(this.targetColor, Gdx.graphics.getDeltaTime() * 8.0F);

        if (this.show) {
            for (int i = 0; i < optionList.size(); i++) {
                ((LargeDialogOptionButton) optionList.get(i)).update(optionList.size());
                if (((LargeDialogOptionButton) optionList.get(i)).pressed && waitForInput) {
                    selectedOption = i;
                    ((LargeDialogOptionButton) optionList.get(i)).pressed = false;
                    waitForInput = false;
                }
            }
        }

        if (Settings.lineBreakViaCharacter) {
            bodyTextEffectCN();
        } else {
            bodyTextEffect();
        }
        for (DialogWord w : this.words) {
            w.update();
        }
    }

    public int getSelectedOption() {
        waitForInput = true;
        return selectedOption;
    }

    public void clear() {
        optionList.clear();
        this.words.clear();
        waitForInput = true;
    }

    public void show() {
        this.targetColor = PANEL_COLOR;
        if (Settings.FAST_MODE) {
            this.animateTimer = 0.125F;
        } else {
            this.animateTimer = 0.5F;
        }
        this.show = true;
        this.isMoving = true;
    }

    public void show(String text) {
        updateBodyText(text);
        show();
    }

    public void hide() {
        this.targetColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
        if (Settings.FAST_MODE) {
            this.animateTimer = 0.125F;
        } else {
            this.animateTimer = 0.5F;
        }
        this.show = false;
        this.isMoving = true;
        for (DialogWord w : this.words) {
            w.dialogFadeOut();
        }
        optionList.clear();
    }

    public void removeDialogOption(int slot) {
        optionList.remove(slot);
    }

    public void addDialogOption(String text) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text));
    }

    public void addDialogOption(String text, AbstractCard previewCard) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, previewCard));
    }

    public void addDialogOption(String text, AbstractRelic previewRelic) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, previewRelic));
    }

    public void addDialogOption(String text, AbstractCard previewCard, AbstractRelic previewRelic) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, previewCard, previewRelic));
    }

    public void addDialogOption(String text, boolean isDisabled) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, isDisabled));
    }

    public void addDialogOption(String text, boolean isDisabled, AbstractCard previewCard) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, isDisabled, previewCard));
    }

    public void addDialogOption(String text, boolean isDisabled, AbstractRelic previewRelic) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, isDisabled, previewRelic));
    }

    public void addDialogOption(String text, boolean isDisabled, AbstractCard previewCard, AbstractRelic previewRelic) {
        optionList.add(new LargeDialogOptionButton(optionList.size(), text, isDisabled, previewCard, previewRelic));
    }

    public void updateDialogOption(int slot, String text) {
        optionList.set(slot, new LargeDialogOptionButton(slot, text));
    }

    public void updateBodyText(String text) {
        updateBodyText(text, DialogWord.AppearEffect.BUMP_IN);
    }

    public void updateBodyText(String text, DialogWord.AppearEffect ae) {
        this.s = new Scanner(text);
        this.words.clear();
        this.textDone = false;
        this.a_effect = ae;
        this.curLineWidth = 0.0F;
        this.curLine = 0;
    }

    public void clearRemainingOptions() {
        for (int i = optionList.size() - 1; i > 0; i--) {
            optionList.remove(i);
        }
    }

    private void bodyTextEffectCN() {
        this.wordTimer -= Gdx.graphics.getDeltaTime();
        if (this.wordTimer < 0.0F && !this.textDone) {
            if (Settings.FAST_MODE) {
                this.wordTimer = 0.005F;
            } else {
                this.wordTimer = 0.02F;
            }

            if (this.s.hasNext()) {
                String word = this.s.next();

                if (word.equals("NL")) {
                    this.curLine++;
                    this.curLineWidth = 0.0F;

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

                    this.gl.setText(FontHelper.charDescFont, tmp);
                    if (this.curLineWidth + this.gl.width > DIALOG_MSG_W) {
                        this.curLine++;
                        this.curLineWidth = this.gl.width;
                    } else {
                        this.curLineWidth += this.gl.width;
                    }

                    this.words.add(new DialogWord(FontHelper.charDescFont, tmp, this.a_effect, effect, color,
                            DIALOG_MSG_X + this.curLineWidth - this.gl.width,
                            DIALOG_MSG_Y - LINE_SPACING * this.curLine, this.curLine));

                    if (!this.show) {
                        ((DialogWord) this.words.get(this.words.size() - 1)).dialogFadeOut();
                    }
                }
            } else {
                this.textDone = true;
                this.s.close();
            }
        }
    }

    private void bodyTextEffect() {
        this.wordTimer -= Gdx.graphics.getDeltaTime();
        if (this.wordTimer < 0.0F && !this.textDone) {
            if (Settings.FAST_MODE) {
                this.wordTimer = 0.005F;
            } else {
                this.wordTimer = 0.02F;
            }

            if (this.s.hasNext()) {
                String word = this.s.next();

                if (word.equals("NL")) {
                    this.curLine++;
                    this.curLineWidth = 0.0F;

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

                this.gl.setText(FontHelper.charDescFont, word);
                if (this.curLineWidth + this.gl.width > DIALOG_MSG_W) {
                    this.curLine++;
                    this.curLineWidth = this.gl.width + CHAR_SPACING;
                } else {
                    this.curLineWidth += this.gl.width + CHAR_SPACING;
                }

                this.words.add(new DialogWord(FontHelper.charDescFont, word, this.a_effect, effect, color,
                        DIALOG_MSG_X + this.curLineWidth - this.gl.width, DIALOG_MSG_Y - LINE_SPACING * this.curLine,
                        this.curLine));

                if (!this.show) {
                    ((DialogWord) this.words.get(this.words.size() - 1)).dialogFadeOut();
                }
            } else {

                this.textDone = true;
                this.s.close();
            }
        }
    }

    public void render(SpriteBatch sb) {
        if (!AbstractDungeon.player.isDead) {
            for (DialogWord w : this.words) {
                w.render(sb, Settings.HEIGHT - 525.0F * Settings.scale);
            }

            for (LargeDialogOptionButton b : optionList) {
                b.render(sb);
            }

            for (LargeDialogOptionButton b : optionList) {
                b.renderCardPreview(sb);
            }

            for (LargeDialogOptionButton b : optionList)
                b.renderRelicPreview(sb);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\
 * RoomEventDialog.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

