package com.megacrit.cardcrawl.trials;

import com.megacrit.cardcrawl.characters.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class CustomTrial
        extends AbstractTrial {
    private boolean isKeepingStarterRelic = true;
    private ArrayList<String> relicIds = new ArrayList<>();
    private boolean isKeepingStarterCards = true;
    private ArrayList<String> cardIds = new ArrayList<>();
    private ArrayList<String> dailyModIds = new ArrayList<>();
    private boolean useRandomDailyMods;
    private Integer maxHpOverride = null;

    public void setMaxHpOverride(int maxHp) {
        this.maxHpOverride = Integer.valueOf(maxHp);
    }

    public void addStarterCards(List<String> moreCardIds) {
        this.cardIds.addAll(moreCardIds);
    }

    public void setStarterCards(List<String> starterCards) {
        this.cardIds.clear();
        this.cardIds.addAll(starterCards);
        this.isKeepingStarterCards = false;
    }

    public void addStarterRelic(String relicId) {
        this.relicIds.add(relicId);
    }

    public void addStarterRelics(List<String> moreRelics) {
        this.relicIds.addAll(moreRelics);
    }

    public void setStarterRelics(List<String> starterRelics) {
        this.relicIds.clear();
        this.relicIds.addAll(starterRelics);
        this.isKeepingStarterRelic = false;
    }

    public void setShouldKeepStarterRelic(boolean shouldKeep) {
        this.isKeepingStarterRelic = shouldKeep;
    }

    public void addDailyMod(String modId) {
        this.dailyModIds.add(modId);
    }

    public void addDailyMods(List<String> moreDailyMods) {
        this.dailyModIds.addAll(moreDailyMods);
    }

    public void setRandomDailyMods() {
        this.useRandomDailyMods = true;
    }

    public AbstractPlayer setupPlayer(AbstractPlayer player) {
        if (this.maxHpOverride != null) {
            player.maxHealth = this.maxHpOverride.intValue();
            player.currentHealth = this.maxHpOverride.intValue();
        }
        return player;
    }

    public boolean keepStarterRelic() {
        return this.isKeepingStarterRelic;
    }

    public List<String> extraStartingRelicIDs() {
        return this.relicIds;
    }

    public boolean keepsStarterCards() {
        return this.isKeepingStarterCards;
    }

    public List<String> extraStartingCardIDs() {
        return this.cardIds;
    }

    public boolean useRandomDailyMods() {
        return this.useRandomDailyMods;
    }

    public ArrayList<String> dailyModIDs() {
        return this.dailyModIds;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\trials\CustomTrial
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

