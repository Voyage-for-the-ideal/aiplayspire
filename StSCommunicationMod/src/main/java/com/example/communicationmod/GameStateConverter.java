package com.example.communicationmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.buttons.EndTurnButton;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import basemod.ReflectionHacks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameStateConverter {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, Object> getCardInfo(String cardId) {
        AbstractCard card = CardLibrary.getCard(cardId);
        if (card == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Card not found: " + cardId);
            return error;
        }
        return convertCardToJson(card, -1);
    }

    public static String getGameStateJson() {
        if (!CardCrawlGame.isInARun() || AbstractDungeon.player == null || AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null) {
            return "{\"error\": \"Game not running or player not initialized\"}";
        }

        Map<String, Object> state = new HashMap<>();

        // Player Info
        Map<String, Object> playerInfo = new HashMap<>();
        playerInfo.put("current_hp", AbstractDungeon.player.currentHealth);
        playerInfo.put("max_hp", AbstractDungeon.player.maxHealth);
        playerInfo.put("block", AbstractDungeon.player.currentBlock);
        playerInfo.put("energy", EnergyPanel.totalCount); // Correct way to get energy
        playerInfo.put("gold", AbstractDungeon.player.gold);
        
        // Extended Player Info
        if (!AbstractDungeon.player.powers.isEmpty()) {
            playerInfo.put("powers", convertCreaturePowersToJson(AbstractDungeon.player));
        }
        if (AbstractDungeon.player.orbs != null && !AbstractDungeon.player.orbs.isEmpty() && AbstractDungeon.player.maxOrbs > 0) {
            playerInfo.put("orbs", convertOrbsToJson(AbstractDungeon.player.orbs));
        }
        state.put("player", playerInfo);
        
        // Relics & Potions
        state.put("relics", convertRelicsToJson(AbstractDungeon.player.relics));
        state.put("potions", convertPotionsToJson(AbstractDungeon.player.potions));

        // Cards
        state.put("hand", convertCardGroup(AbstractDungeon.player.hand.group));
        state.put("draw_pile", convertCardGroup(AbstractDungeon.player.drawPile.group));
        state.put("discard_pile", convertCardGroup(AbstractDungeon.player.discardPile.group));
        state.put("exhaust_pile", convertCardGroup(AbstractDungeon.player.exhaustPile.group));
        
        // Legacy size fields for compatibility
        state.put("draw_pile_size", AbstractDungeon.player.drawPile.size());
        state.put("discard_pile_size", AbstractDungeon.player.discardPile.size());
        state.put("exhaust_pile_size", AbstractDungeon.player.exhaustPile.size());

        // Monsters
        List<Map<String, Object>> monsters = new ArrayList<>();
        if (AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT) {
            for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (!m.isDeadOrEscaped()) {
                    monsters.add(convertMonsterToJson(m));
                }
            }
        }
        state.put("monsters", monsters);

        // Room Info
        state.put("floor", AbstractDungeon.floorNum);
        state.put("act", AbstractDungeon.actNum);
        state.put("room_phase", AbstractDungeon.getCurrRoom().phase.name());

        // Screen Info
        state.put("screen_type", ChoiceScreenUtils.getCurrentChoiceType().name());
        state.put("choice_list", ChoiceScreenUtils.getCurrentChoiceList());
        state.put("can_proceed", ChoiceScreenUtils.isConfirmButtonAvailable());
        state.put("can_cancel", ChoiceScreenUtils.isCancelButtonAvailable());

        // Turn Info
        boolean isEndTurnButtonEnabled = false;
        if (AbstractDungeon.overlayMenu != null && AbstractDungeon.overlayMenu.endTurnButton != null) {
            isEndTurnButtonEnabled = (boolean) ReflectionHacks.getPrivate(AbstractDungeon.overlayMenu.endTurnButton, EndTurnButton.class, "enabled");
        }
        state.put("is_end_turn_button_enabled", isEndTurnButtonEnabled);

        String json = gson.toJson(state);
        // System.out.println("Generated Game State: " + json); // Too verbose, maybe just summary
        System.out.println("State Summary: Floor=" + AbstractDungeon.floorNum + ", HP=" + AbstractDungeon.player.currentHealth + 
                           ", Screen=" + state.get("screen_type") + ", EndTurnEnabled=" + isEndTurnButtonEnabled);
        return json;
    }

    private static List<Map<String, Object>> convertCardGroup(ArrayList<AbstractCard> cards) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            list.add(convertCardToJson(cards.get(i), i));
        }
        return list;
    }
    
    private static Map<String, Object> convertCardToJson(AbstractCard card, int index) {
        Map<String, Object> jsonCard = new HashMap<>();
        // Essential fields
        jsonCard.put("index", index);
        jsonCard.put("name", card.name);
        jsonCard.put("uuid", card.uuid.toString());
        jsonCard.put("id", card.cardID);
        jsonCard.put("type", card.type.name());
        jsonCard.put("cost", card.cost);
        jsonCard.put("cost_for_turn", card.costForTurn);
        jsonCard.put("target", card.target.name());

        // Upgrades
        if (card.timesUpgraded > 0) {
            jsonCard.put("upgrades", card.timesUpgraded);
        }

        // Playability (only valid in combat context really, but useful)
        if (AbstractDungeon.getMonsters() != null) {
            jsonCard.put("is_playable", card.canUse(AbstractDungeon.player, null));
        }

        // Exhaust
        if (card.exhaust) {
            jsonCard.put("exhausts", true);
        }

        // Numeric values
        if (card.damage > 0) {
            jsonCard.put("damage", card.damage);
            if (card.baseDamage != card.damage) {
                jsonCard.put("base_damage", card.baseDamage);
            }
        }
        if (card.block > 0) {
            jsonCard.put("block", card.block);
            if (card.baseBlock != card.block) {
                jsonCard.put("base_block", card.baseBlock);
            }
        }
        if (card.magicNumber > 0) {
            jsonCard.put("magic_number", card.magicNumber);
            if (card.baseMagicNumber != card.magicNumber) {
                jsonCard.put("base_magic_number", card.baseMagicNumber);
            }
        }
        
        return jsonCard;
    }

    private static Map<String, Object> convertMonsterToJson(AbstractMonster monster) {
        Map<String, Object> jsonMonster = new HashMap<>();
        jsonMonster.put("id", monster.id);
        jsonMonster.put("name", monster.name);
        jsonMonster.put("current_hp", monster.currentHealth);
        jsonMonster.put("max_hp", monster.maxHealth);
        jsonMonster.put("block", monster.currentBlock);
        jsonMonster.put("intent", monster.intent.name());
        
        // Move info (Damage intent)
        EnemyMoveInfo moveInfo = (EnemyMoveInfo)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "move");
        if (moveInfo != null) {
            Map<String, Object> move = new HashMap<>();
            int intentDmg = (int)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "intentDmg");
            // Use intentDmg if positive, otherwise baseDamage might be relevant
            int adjustedDamage = (moveInfo.baseDamage > 0) ? intentDmg : moveInfo.baseDamage;
            
            if (adjustedDamage > 0) {
                move.put("damage", adjustedDamage);
                int hits = moveInfo.isMultiDamage ? moveInfo.multiplier : 1;
                if (hits > 1) {
                    move.put("hits", hits);
                }
            }
            if (!move.isEmpty()) {
                jsonMonster.put("move", move);
            }
        }

        if (!monster.powers.isEmpty()) {
            jsonMonster.put("powers", convertCreaturePowersToJson(monster));
        }
        
        return jsonMonster;
    }

    private static List<Object> convertCreaturePowersToJson(AbstractCreature creature) {
        List<Object> powers = new ArrayList<>();
        for (AbstractPower power : creature.powers) {
            Map<String, Object> jsonPower = new HashMap<>();
            jsonPower.put("id", power.ID);
            jsonPower.put("name", power.name);
            if (power.amount != 0) {
                jsonPower.put("amount", power.amount);
            }
            powers.add(jsonPower);
        }
        return powers;
    }

    private static List<Object> convertRelicsToJson(ArrayList<AbstractRelic> relics) {
        List<Object> result = new ArrayList<>();
        for (AbstractRelic relic : relics) {
            Map<String, Object> jsonRelic = new HashMap<>();
            jsonRelic.put("id", relic.relicId);
            jsonRelic.put("name", relic.name);
            if (relic.counter >= 0) {
                jsonRelic.put("counter", relic.counter);
            }
            result.add(jsonRelic);
        }
        return result;
    }

    private static List<Object> convertPotionsToJson(ArrayList<AbstractPotion> potions) {
        List<Object> result = new ArrayList<>();
        for (AbstractPotion potion : potions) {
            Map<String, Object> jsonPotion = new HashMap<>();
            jsonPotion.put("id", potion.ID);
            jsonPotion.put("name", potion.name);
            
            if (potion instanceof PotionSlot) {
                jsonPotion.put("is_empty", true);
            } else {
                jsonPotion.put("can_use", potion.canUse());
                jsonPotion.put("can_discard", potion.canDiscard());
                if (potion.isThrown) {
                    jsonPotion.put("requires_target", true);
                }
            }
            result.add(jsonPotion);
        }
        return result;
    }
    
    private static List<Object> convertOrbsToJson(ArrayList<AbstractOrb> orbs) {
        List<Object> result = new ArrayList<>();
        for (AbstractOrb orb : orbs) {
            Map<String, Object> jsonOrb = new HashMap<>();
            jsonOrb.put("id", orb.ID);
            jsonOrb.put("name", orb.name); // Orbs usually have IDs like "Lightning", "Frost"
            if (orb.evokeAmount > 0) {
                jsonOrb.put("evoke", orb.evokeAmount);
            }
            if (orb.passiveAmount > 0) {
                jsonOrb.put("passive", orb.passiveAmount);
            }
            result.add(jsonOrb);
        }
        return result;
    }
}
