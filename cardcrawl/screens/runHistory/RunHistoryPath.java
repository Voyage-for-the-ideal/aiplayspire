package com.megacrit.cardcrawl.screens.runHistory;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.screens.stats.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RunHistoryPath {
    public static final String BOSS_TREASURE_LABEL = "T!";
    public static final String HEART_LABEL = "<3";
    public static final int MAX_NODES_PER_ROW = 20;
    private RunData runData;
    public List<RunPathElement> pathElements = new ArrayList<>();

    private int rows = 0;

    public void setRunData(RunData newData) {
        this.runData = newData;

        List<String> labels = new ArrayList<>();
        int choiceIndex = 0, outcomeIndex = 0;
        List<String> choices = this.runData.path_taken;
        List<String> outcomes = this.runData.path_per_floor;
        if (choices == null) {
            choices = new LinkedList<>();
        }
        if (outcomes == null) {
            outcomes = new LinkedList<>();
        }

        int bossTreasureCount = 0;

        while (choiceIndex < choices.size() || outcomeIndex < outcomes.size()) {
            String choice = (choiceIndex < choices.size()) ? choices.get(choiceIndex) : "_";
            String outcome = (outcomeIndex < outcomes.size()) ? outcomes.get(outcomeIndex) : "_";
            if (outcome == null) {
                outcomeIndex++;
                if (bossTreasureCount < 2) {
                    labels.add("T!");
                    bossTreasureCount++;
                    continue;
                }
                if (bossTreasureCount == 2) {
                    labels.add("<3");
                    bossTreasureCount++;
                    continue;
                }
                labels.add("null");
                continue;
            }
            if (choice.equals("?") && !outcome.equals("?")) {
                labels.add("?(" + outcome + ")");
            } else {
                labels.add(choice);
            }
            choiceIndex++;
            outcomeIndex++;
        }

        HashMap<Integer, BattleStats> battlesByFloor = new HashMap<>();
        for (BattleStats battle : this.runData.damage_taken) {
            battlesByFloor.put(Integer.valueOf(battle.floor), battle);
        }

        HashMap<Integer, EventStats> eventsByFloor = new HashMap<>();
        for (EventStats event : this.runData.event_choices) {
            eventsByFloor.put(Integer.valueOf(event.floor), event);
        }

        HashMap<Integer, CardChoiceStats> cardsByFloor = new HashMap<>();
        for (CardChoiceStats choice : this.runData.card_choices) {
            cardsByFloor.put(Integer.valueOf(choice.floor), choice);
        }

        HashMap<Integer, List<String>> relicsByFloor = new HashMap<>();
        if (this.runData.relics_obtained != null) {
            for (ObtainStats relicData : this.runData.relics_obtained) {
                if (!relicsByFloor.containsKey(Integer.valueOf(relicData.floor))) {
                    relicsByFloor.put(Integer.valueOf(relicData.floor), new ArrayList<>());
                }
                ((List<String>) relicsByFloor.get(Integer.valueOf(relicData.floor))).add(relicData.key);
            }
        }

        HashMap<Integer, List<String>> potionsByFloor = new HashMap<>();
        if (this.runData.potions_obtained != null) {
            for (ObtainStats potionData : this.runData.potions_obtained) {
                if (!potionsByFloor.containsKey(Integer.valueOf(potionData.floor))) {
                    potionsByFloor.put(Integer.valueOf(potionData.floor), new ArrayList<>());
                }
                ((List<String>) potionsByFloor.get(Integer.valueOf(potionData.floor))).add(potionData.key);
            }
        }

        HashMap<Integer, CampfireChoice> campfireChoicesByFloor = new HashMap<>();
        if (this.runData.campfire_choices != null) {
            for (CampfireChoice choice : this.runData.campfire_choices) {
                campfireChoicesByFloor.put(Integer.valueOf(choice.floor), choice);
            }
        }

        HashMap<Integer, ArrayList<String>> purchasesByFloor = new HashMap<>();
        if (this.runData.items_purchased != null && this.runData.item_purchase_floors != null
                && this.runData.items_purchased
                        .size() == this.runData.item_purchase_floors.size()) {
            for (int j = 0; j < this.runData.items_purchased.size(); j++) {
                int floor = ((Integer) this.runData.item_purchase_floors.get(j)).intValue();
                String key = this.runData.items_purchased.get(j);
                if (!purchasesByFloor.containsKey(Integer.valueOf(floor))) {
                    purchasesByFloor.put(Integer.valueOf(floor), new ArrayList<>());
                }
                ((ArrayList<String>) purchasesByFloor.get(Integer.valueOf(floor))).add(key);
            }
        }

        HashMap<Integer, ArrayList<String>> purgesByFloor = new HashMap<>();
        if (this.runData.items_purged != null && this.runData.items_purged_floors != null && this.runData.items_purged
                .size() == this.runData.items_purged_floors.size()) {
            for (int j = 0; j < this.runData.items_purged.size(); j++) {
                int floor = ((Integer) this.runData.items_purged_floors.get(j)).intValue();
                String key = this.runData.items_purged.get(j);
                if (!purgesByFloor.containsKey(Integer.valueOf(floor))) {
                    purgesByFloor.put(Integer.valueOf(floor), new ArrayList<>());
                }
                ((ArrayList<String>) purgesByFloor.get(Integer.valueOf(floor))).add(key);
            }
        }

        this.pathElements.clear();

        this.rows = 0;
        int bossRelicChoiceIndex = 0;
        int tmpColumn = 0;

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int floor = i + 1;
            RunPathElement element = new RunPathElement(label, floor);

            if (this.runData.current_hp_per_floor != null && this.runData.max_hp_per_floor != null
                    && i < this.runData.current_hp_per_floor
                            .size()
                    && i < this.runData.max_hp_per_floor.size()) {
                element.addHpData(this.runData.current_hp_per_floor.get(i), this.runData.max_hp_per_floor.get(i));
            }
            if (this.runData.gold_per_floor != null && i < this.runData.gold_per_floor.size()) {
                element.addGoldData(this.runData.gold_per_floor.get(i));
            }

            if (battlesByFloor.containsKey(Integer.valueOf(floor))) {
                element.addBattleData(battlesByFloor.get(Integer.valueOf(floor)));
            }
            if (eventsByFloor.containsKey(Integer.valueOf(floor))) {
                element.addEventData(eventsByFloor.get(Integer.valueOf(floor)));
            }
            if (cardsByFloor.containsKey(Integer.valueOf(floor))) {
                element.addCardChoiceData(cardsByFloor.get(Integer.valueOf(floor)));
            }
            if (relicsByFloor.containsKey(Integer.valueOf(floor))) {
                element.addRelicObtainStats(relicsByFloor.get(Integer.valueOf(floor)));
            }
            if (potionsByFloor.containsKey(Integer.valueOf(floor))) {
                element.addPotionObtainStats(potionsByFloor.get(Integer.valueOf(floor)));
            }
            if (campfireChoicesByFloor.containsKey(Integer.valueOf(floor))) {
                element.addCampfireChoiceData(campfireChoicesByFloor.get(Integer.valueOf(floor)));
            }
            if (purchasesByFloor.containsKey(Integer.valueOf(floor))) {
                element.addShopPurchaseData(purchasesByFloor.get(Integer.valueOf(floor)));
            }
            if (purgesByFloor.containsKey(Integer.valueOf(floor))) {
                element.addPurgeData(purgesByFloor.get(Integer.valueOf(floor)));
            }

            if (label.equals("T!") && this.runData.boss_relics != null &&
                    bossRelicChoiceIndex < this.runData.boss_relics.size()) {
                element.addRelicObtainStats(
                        ((BossRelicChoiceStats) this.runData.boss_relics.get(bossRelicChoiceIndex)).picked);
                bossRelicChoiceIndex++;
            }

            this.pathElements.add(element);
            element.col = tmpColumn++;
            element.row = this.rows;

            if (isActEndNode(element) || i == labels.size() - 1) {
                tmpColumn = 0;
                this.rows++;
            }
        }

        this.rows = Math.max(this.rows, this.pathElements.size() / 20);
    }

    public void update() {
        boolean isHovered = false;
        for (RunPathElement element : this.pathElements) {
            element.update();
            if (element.isHovered()) {
                isHovered = true;
            }
        }
        if (isHovered) {
            CardCrawlGame.cursor.changeType(GameCursor.CursorType.INSPECT);
        }
    }

    private boolean isActEndNode(RunPathElement element) {
        return (element.nodeType == RunPathElement.PathNodeType.BOSS_TREASURE
                || element.nodeType == RunPathElement.PathNodeType.HEART);
    }

    public void render(SpriteBatch sb, float x, float y) {
        float offsetX = x;
        float offsetY = y;

        int sinceOffset = 0;
        for (RunPathElement element : this.pathElements) {
            element.position(offsetX, offsetY);

            if (isActEndNode(element) || sinceOffset > 20) {
                offsetX = x;
                offsetY -= RunPathElement.getApproximateHeight();
                sinceOffset = 0;
                continue;
            }
            offsetX += RunPathElement.getApproximateWidth();
            sinceOffset++;
        }

        for (RunPathElement element : this.pathElements) {
            element.render(sb);
        }
    }

    public float approximateHeight() {
        return this.rows * RunPathElement.getApproximateHeight();
    }

    public float approximateMaxWidth() {
        return 16.5F * RunPathElement.getApproximateWidth();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\runHistory
 * \RunHistoryPath.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

