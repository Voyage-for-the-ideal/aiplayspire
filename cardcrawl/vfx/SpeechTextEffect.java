package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.ui.DialogWord;
import com.megacrit.cardcrawl.ui.SpeechWord;

import java.util.ArrayList;
import java.util.Scanner;

public class SpeechTextEffect
        extends AbstractGameEffect {
    private static GlyphLayout gl;
    private BitmapFont font;
    private DialogWord.AppearEffect a_effect;
    private static final float DEFAULT_WIDTH = 280.0F * Settings.scale;
    private static final float LINE_SPACING = Settings.isMobile ? (18.0F * Settings.scale) : (15.0F * Settings.scale);
    private static final float CHAR_SPACING = 8.0F * Settings.scale;
    private static final float WORD_TIME = 0.03F;
    private float wordTimer = 0.0F;

    private boolean textDone = false;
    private ArrayList<SpeechWord> words = new ArrayList<>();
    private float x;
    private float y;
    private int curLine = 0;
    private Scanner s;
    private float curLineWidth = 0.0F;
    private static final float FADE_TIME = 0.3F;

    public SpeechTextEffect(float x, float y, float duration, String msg, DialogWord.AppearEffect a_effect) {
        if (gl == null) {
            gl = new GlyphLayout();
        }

        this.duration = duration;
        this.x = x;
        this.y = y;
        this.font = FontHelper.turnNumFont;
        this.a_effect = a_effect;
        this.s = new Scanner(msg);
    }

    public void update() {
        this.wordTimer -= Gdx.graphics.getDeltaTime();
        if (this.wordTimer < 0.0F && !this.textDone) {
            if (Settings.lineBreakViaCharacter) {
                addWordCN();
            } else {
                addWord();
            }
        }

        for (SpeechWord w : this.words) {
            w.update();
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.words.clear();
            this.isDone = true;
        }

        if (this.duration < 0.3F) {
            for (SpeechWord w : this.words) {
                w.fadeOut();
            }
        }
    }

    private void addWord() {
        this.wordTimer = 0.03F;

        if (this.s.hasNext()) {
            String word = this.s.next();

            if (word.equals("NL")) {
                this.curLine++;
                for (SpeechWord w : this.words) {
                    w.shiftY(LINE_SPACING);
                }
                this.curLineWidth = 0.0F;

                return;
            }
            DialogWord.WordColor color = SpeechWord.identifyWordColor(word);
            if (color != DialogWord.WordColor.DEFAULT) {
                word = word.substring(2, word.length());
            }

            DialogWord.WordEffect effect = SpeechWord.identifyWordEffect(word);
            if (effect != DialogWord.WordEffect.NONE) {
                word = word.substring(1, word.length() - 1);
            }

            gl.setText(this.font, word);
            float temp = 0.0F;

            if (this.curLineWidth + gl.width > DEFAULT_WIDTH) {
                this.curLine++;
                for (SpeechWord w : this.words) {
                    w.shiftY(LINE_SPACING);
                }
                this.curLineWidth = gl.width + CHAR_SPACING;
                temp = -this.curLineWidth / 2.0F;
            } else {

                this.curLineWidth += gl.width;
                temp = -this.curLineWidth / 2.0F;

                for (SpeechWord w : this.words) {
                    if (w.line == this.curLine) {
                        w.setX(this.x + temp);
                        gl.setText(this.font, w.word);
                        temp += gl.width + CHAR_SPACING;
                    }
                }

                this.curLineWidth += CHAR_SPACING;
                gl.setText(this.font, word + " ");
            }

            this.words.add(new SpeechWord(this.font, word, this.a_effect, effect, color, this.x + temp,
                    this.y - LINE_SPACING * this.curLine, this.curLine));
        } else {

            this.textDone = true;
            this.s.close();
        }
    }

    private void addWordCN() {
        this.wordTimer = 0.03F;

        if (this.s.hasNext()) {
            String word = this.s.next();

            if (word.equals("NL")) {
                this.curLine++;
                for (SpeechWord w : this.words) {
                    w.shiftY(LINE_SPACING);
                }
                this.curLineWidth = 0.0F;

                return;
            }
            DialogWord.WordColor color = SpeechWord.identifyWordColor(word);
            if (color != DialogWord.WordColor.DEFAULT) {
                word = word.substring(2, word.length());
            }

            DialogWord.WordEffect effect = SpeechWord.identifyWordEffect(word);
            if (effect != DialogWord.WordEffect.NONE) {
                word = word.substring(1, word.length() - 1);
            }

            for (int i = 0; i < word.length(); i++) {
                String tmp = Character.toString(word.charAt(i));

                gl.setText(this.font, tmp);
                float temp = 0.0F;

                if (this.curLineWidth + gl.width > DEFAULT_WIDTH) {
                    this.curLine++;
                    for (SpeechWord w : this.words) {
                        w.shiftY(LINE_SPACING);
                    }
                    this.curLineWidth = gl.width;
                    temp = -this.curLineWidth / 2.0F;
                } else {

                    this.curLineWidth += gl.width;
                    temp = -this.curLineWidth / 2.0F;

                    for (SpeechWord w : this.words) {
                        if (w.line == this.curLine) {
                            w.setX(this.x + temp);
                            gl.setText(this.font, w.word);
                            temp += gl.width;
                        }
                    }
                    gl.setText(this.font, tmp + " ");
                }

                this.words.add(new SpeechWord(this.font, tmp, this.a_effect, effect, color, this.x + temp,
                        this.y - LINE_SPACING * this.curLine, this.curLine));
            }
        } else {

            this.textDone = true;
            this.s.close();
        }
    }

    public void render(SpriteBatch sb) {
        for (SpeechWord w : this.words)
            w.render(sb);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * SpeechTextEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */
