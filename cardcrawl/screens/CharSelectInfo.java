package com.megacrit.cardcrawl.screens;

import com.megacrit.cardcrawl.characters.AbstractPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CharSelectInfo {
    public String name;
    public String flavorText;
    public String hp;
    public int gold;
    public int currentHp;
    public int maxHp;
    public int maxOrbs;
    public int cardDraw;
    public int floorNum;
    public String levelName;
    public long saveDate;
    public AbstractPlayer player;
    public String deckString;
    public ArrayList<String> relics;
    public ArrayList<String> deck;
    public boolean resumeGame;
    public boolean isHardMode;

    public CharSelectInfo(String name, String flavorText, int currentHp, int maxHp, int maxOrbs, int gold, int cardDraw,
            AbstractPlayer player, ArrayList<String> relics, ArrayList<String> deck, boolean resumeGame) {
        this.name = name;
        this.flavorText = flavorText;
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.maxOrbs = maxOrbs;
        this.hp = Integer.toString(currentHp) + "/" + Integer.toString(maxHp);
        this.gold = gold;
        this.cardDraw = cardDraw;
        this.relics = relics;
        this.deck = deck;
        this.player = player;
        this.resumeGame = resumeGame;

        if (!resumeGame) {
            setDeck();
        }
    }

    public CharSelectInfo(String name, String fText, int currentHp, int maxHp, int maxOrbs, int gold, int cardDraw,
            AbstractPlayer player, ArrayList<String> relics, ArrayList<String> deck, long saveDate, int floorNum,
            String levelName, boolean isHardMode) {
        this(name, fText, currentHp, maxHp, maxOrbs, gold, cardDraw, player, relics, deck, true);
        this.isHardMode = isHardMode;
        this.saveDate = saveDate;
        this.floorNum = floorNum;
        this.levelName = levelName;
    }

    private void setDeck() {
        this.deckString = createDeckInfoString(this.player.getStartingDeck());
    }

    public static String createDeckInfoString(ArrayList<String> deck) {
        HashMap<String, Integer> cards = new HashMap<>();

        for (String s : deck) {
            if (!cards.containsKey(s)) {
                cards.put(s, Integer.valueOf(1));
                continue;
            }
            cards.put(s, Integer.valueOf(((Integer) cards.get(s)).intValue() + 1));
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> c : cards.entrySet()) {
            sb.append("#b").append(c.getValue()).append(" ").append(c.getKey());
            if (((Integer) c.getValue()).intValue() > 1) {
                sb.append("s");
            }
            sb.append(", ");
        }
        String retVal = sb.toString();

        if (retVal.length() > 80) {
            return "Click the deck icon to view starting cards.";
        }
        return retVal.substring(0, retVal.length() - 2);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * CharSelectInfo.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

