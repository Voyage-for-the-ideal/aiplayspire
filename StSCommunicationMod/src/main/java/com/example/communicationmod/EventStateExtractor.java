package com.example.communicationmod;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Extracts locale-independent semantics for the base game's event state. */
public final class EventStateExtractor {
    private static final Set<String> VANILLA_EVENTS = new HashSet<>(Arrays.asList(
        "BigFish", "Cleric", "DeadAdventurer", "GoldenIdolEvent", "GoldenWing", "GoopPuddle",
        "LivingWall", "Mushrooms", "ScrapOoze", "ShiningLight", "Sssserpent",
        "Addict", "BackToBasics", "Beggar", "Colosseum", "CursedTome", "DrugDealer",
        "ForgottenAltar", "Ghosts", "KnowingSkull", "MaskedBandits", "Nest", "TheJoust",
        "TheLibrary", "TheMausoleum", "Vampires",
        "Falling", "MindBloom", "MoaiHead", "MysteriousSphere", "SecretPortal", "SensoryStone",
        "SpireHeart", "TombRedMask", "WindingHalls",
        "AccursedBlacksmith", "Bonfire", "Designer", "Duplicator", "FaceTrader",
        "FountainOfCurseRemoval", "GoldShrine", "GremlinMatchGame", "GremlinWheelGame", "Lab",
        "Nloth", "NoteForYourself", "PurificationShrine", "Transmogrifier", "UpgradeShrine",
        "WeMeetAgain", "WomanInBlue"
    ));

    private static final Set<String> COMPLEX_EVENTS = new HashSet<>(Arrays.asList(
        "DeadAdventurer", "GoldenIdolEvent", "Mushrooms", "ScrapOoze", "ShiningLight", "Addict",
        "Colosseum", "CursedTome", "TheJoust", "TheLibrary", "TheMausoleum", "MindBloom",
        "MysteriousSphere", "SensoryStone", "GremlinMatchGame", "GremlinWheelGame", "Lab",
        "FaceTrader", "Bonfire", "Designer", "DrugDealer", "NoteForYourself"
    ));

    private static final Map<String, String> EVENT_IDS = new HashMap<>();
    private static final Map<AbstractEvent, Map<UUID, Integer>> MATCH_GAME_SLOTS =
        Collections.synchronizedMap(new WeakHashMap<AbstractEvent, Map<UUID, Integer>>());

    static {
        putId("BigFish", "Big Fish"); putId("Cleric", "The Cleric");
        putId("DeadAdventurer", "Dead Adventurer"); putId("GoldenIdolEvent", "Golden Idol");
        putId("GoldenWing", "Golden Wing"); putId("GoopPuddle", "World of Goop");
        putId("LivingWall", "Living Wall"); putId("Mushrooms", "Mushrooms");
        putId("ScrapOoze", "Scrap Ooze"); putId("ShiningLight", "Shining Light");
        putId("Sssserpent", "Liars Game"); putId("Addict", "Addict");
        putId("BackToBasics", "Back to Basics"); putId("Beggar", "Beggar");
        putId("Colosseum", "Colosseum"); putId("CursedTome", "Cursed Tome");
        putId("DrugDealer", "Drug Dealer"); putId("ForgottenAltar", "Forgotten Altar");
        putId("Ghosts", "Ghosts"); putId("KnowingSkull", "Knowing Skull");
        putId("MaskedBandits", "Masked Bandits"); putId("Nest", "Nest"); putId("TheJoust", "The Joust");
        putId("TheLibrary", "The Library"); putId("TheMausoleum", "The Mausoleum");
        putId("Vampires", "Vampires"); putId("Falling", "Falling"); putId("MindBloom", "MindBloom");
        putId("MoaiHead", "The Moai Head"); putId("SecretPortal", "SecretPortal");
        putId("SensoryStone", "SensoryStone"); putId("SpireHeart", "Spire Heart");
        putId("MysteriousSphere", "Mysterious Sphere");
        putId("TombRedMask", "Tomb of Lord Red Mask"); putId("WindingHalls", "Winding Halls");
        putId("AccursedBlacksmith", "Accursed Blacksmith"); putId("Bonfire", "Bonfire Elementals");
        putId("Designer", "Designer"); putId("Duplicator", "Duplicator"); putId("FaceTrader", "FaceTrader");
        putId("FountainOfCurseRemoval", "Fountain of Cleansing"); putId("GoldShrine", "Golden Shrine");
        putId("GremlinMatchGame", "Match and Keep!"); putId("GremlinWheelGame", "Wheel of Change");
        putId("Lab", "Lab"); putId("Nloth", "N'loth"); putId("NoteForYourself", "NoteForYourself");
        putId("PurificationShrine", "Purifier");
        putId("Transmogrifier", "Transmorgrifier"); putId("UpgradeShrine", "Upgrade Shrine");
        putId("WeMeetAgain", "WeMeetAgain"); putId("WomanInBlue", "The Woman in Blue");
    }

    private EventStateExtractor() {}

    private static void putId(String className, String id) { EVENT_IDS.put(className, id); }

    public static Set<String> registeredEventClasses() {
        return Collections.unmodifiableSet(VANILLA_EVENTS);
    }

    static Map<String, String> registeredEventIds() {
        return Collections.unmodifiableMap(EVENT_IDS);
    }

    public static Map<String, Object> extract(AbstractEvent event, ArrayList<LargeDialogOptionButton> buttons) {
        if (event == null) return null;

        String className = event.getClass().getSimpleName();
        String phase = readPhase(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", EVENT_IDS.containsKey(className) ? EVENT_IDS.get(className) : className);
        result.put("class_name", className);
        result.put("phase", phase);

        List<Map<String, Object>> choices = baseChoices(buttons);
        result.put("choices", choices);
        if (!VANILLA_EVENTS.contains(className)) {
            result.put("semantics_status", "UNKNOWN");
            result.put("decision_kind", "UNKNOWN");
            return result;
        }

        try {
            choices = eventChoices(event, className, phase, buttons);
            result.put("choices", choices);
            describe(event, className, phase, choices);
            result.put("semantics_status", "KNOWN");
            int enabled = enabledCount(choices);
            if (enabled == 1) {
                result.put("decision_kind", "FORCED");
            } else if (COMPLEX_EVENTS.contains(className) || hasComplexEffect(choices)) {
                result.put("decision_kind", "COMPLEX");
            } else {
                result.put("decision_kind", "DETERMINISTIC");
            }
        } catch (RuntimeException ex) {
            System.err.println("Event semantic extraction failed for " + className + ": " + ex.getMessage());
            result.put("semantics_status", "UNKNOWN");
            result.put("decision_kind", "UNKNOWN");
        }
        return result;
    }

    static List<Map<String, Object>> baseChoices(ArrayList<LargeDialogOptionButton> buttons) {
        List<Map<String, Object>> result = new ArrayList<>();
        int actionIndex = 0;
        for (int i = 0; i < buttons.size(); i++) {
            LargeDialogOptionButton button = buttons.get(i);
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("button_index", i);
            choice.put("action_index", button.isDisabled ? null : actionIndex++);
            choice.put("enabled", !button.isDisabled);
            choice.put("label", ChoiceScreenUtils.removeTextFormatting(button.msg));
            choice.put("kind", "UNKNOWN");
            choice.put("outcomes", new ArrayList<Map<String, Object>>());
            choice.put("followup", "NONE");
            result.add(choice);
        }
        return result;
    }

    static ArrayList<String> enabledChoiceLabels(AbstractEvent event, ArrayList<LargeDialogOptionButton> buttons) {
        String className = event == null ? "" : event.getClass().getSimpleName();
        String phase = event == null ? "UNKNOWN" : readPhase(event);
        ArrayList<String> labels = new ArrayList<>();
        try {
            for (Map<String, Object> choice : eventChoices(event, className, phase, buttons)) {
                if (Boolean.TRUE.equals(choice.get("enabled"))) labels.add(String.valueOf(choice.get("label")));
            }
        } catch (RuntimeException ex) {
            for (LargeDialogOptionButton button : buttons) {
                if (!button.isDisabled) labels.add(button.msg);
            }
        }
        return labels;
    }

    private static List<Map<String, Object>> eventChoices(AbstractEvent event, String className, String phase,
                                                           ArrayList<LargeDialogOptionButton> buttons) {
        if ("GremlinMatchGame".equals(className) && "PLAY".equals(phase)) return matchGameChoices(event);
        if ("GremlinWheelGame".equals(className) && "SPIN".equals(phase)) return wheelSpinChoices(event);
        return baseChoices(buttons);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matchGameChoices(AbstractEvent event) {
        CardGroup cards = (CardGroup) requiredField(event, "cards");
        float waitTimer = ((Number) requiredField(event, "waitTimer")).floatValue();
        boolean gameDone = boolField(event, "gameDone");
        Map<UUID, Integer> slots = MATCH_GAME_SLOTS.get(event);
        if (slots == null) {
            slots = new LinkedHashMap<>();
            MATCH_GAME_SLOTS.put(event, slots);
        }
        for (AbstractCard card : cards.group) {
            if (!slots.containsKey(card.uuid)) slots.put(card.uuid, slots.size());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        List<AbstractCard> ordered = new ArrayList<>(cards.group);
        final Map<UUID, Integer> stableSlots = slots;
        Collections.sort(ordered, (left, right) ->
            Integer.compare(stableSlots.get(left.uuid), stableSlots.get(right.uuid)));
        int actionIndex = 0;
        for (AbstractCard card : ordered) {
            int slot = slots.get(card.uuid);
            boolean enabled = waitTimer == 0.0F && !gameDone && card.isFlipped;
            Map<String, Object> effect = effect("reveal_match_card", "slot", slot,
                "revealed_card_id", card.isFlipped ? null : card.cardID);
            Map<String, Object> choice = choice(slot, enabled ? actionIndex++ : null, enabled,
                "slot " + slot, "MINI_GAME_CARD", "EVENT", effect);
            result.add(choice);
        }
        return result;
    }

    private static List<Map<String, Object>> wheelSpinChoices(AbstractEvent event) {
        boolean startSpin = boolField(event, "startSpin");
        boolean buttonPressed = boolField(event, "buttonPressed");
        if (!startSpin || buttonPressed) return new ArrayList<>();
        return Collections.singletonList(choice(0, 0, true, "spin", "MINI_GAME_SPIN", "EVENT",
            effect("spin_wheel", "pool", "GREMLIN_WHEEL")));
    }

    private static Map<String, Object> choice(int buttonIndex, Integer actionIndex, boolean enabled,
                                               String label, String kind, String followup,
                                               Map<String, Object>... effects) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("button_index", buttonIndex);
        choice.put("action_index", actionIndex);
        choice.put("enabled", enabled);
        choice.put("label", label);
        choice.put("kind", kind);
        choice.put("followup", followup);
        choice.put("outcomes", Collections.singletonList(outcome(1.0D, effects)));
        return choice;
    }

    private static void describe(AbstractEvent event, String name, String phase, List<Map<String, Object>> c) {
        boolean start = isStart(phase);
        switch (name) {
            case "LivingWall":
                if (start) { select(c,0,"REMOVE_CARD","GRID","purge",1); select(c,1,"TRANSFORM_CARD","GRID","transform",1); select(c,2,"UPGRADE_CARD","GRID","upgrade",1); }
                break;
            case "Cleric":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",loseGold(35),gainHp(intField(event,"healAmt"))); selectWithCost(c,1,"REMOVE_CARD","GRID","purge",1,intField(event,"purifyCost")); leave(c,2); }
                break;
            case "BigFish":
                if (start) { deterministic(c,0,"EFFECT","NONE",gainHp(intField(event,"healAmt"))); deterministic(c,1,"EFFECT","NONE",gainMaxHp(5)); deterministic(c,2,"RANDOM_REWARD","NONE",randomRelic("ANY"),addCard("Regret",1)); }
                break;
            case "DeadAdventurer":
                if ("INTRO".equals(phase)) {
                    double combat = intField(event,"encounterChance") / 100.0D;
                    double reward = (1.0D - combat) / 3.0D;
                    stochastic(c,0,"STOCHASTIC","EVENT",
                        outcome(combat,startCombat(invokeString(event,"getMonster"))),
                        outcome(reward,gainGold(30)), outcome(reward), outcome(reward,randomRelic("ANY")));
                    leave(c,1);
                }
                break;
            case "GoldenIdolEvent":
                if ("0".equals(phase)) { complex(c,0,"COMMIT","EVENT",obtainRelic("Golden Idol"),effect("future_cost_choice","card_id","Injury","amount",intField(event,"damage"),"max",intField(event,"maxHpLoss"),"result","Injury OR HP loss OR max HP loss")); leave(c,1); }
                else if ("1".equals(phase)) { deterministic(c,0,"EFFECT","NONE",addCard("Injury",1)); deterministic(c,1,"EFFECT","NONE",loseHp(intField(event,"damage"))); deterministic(c,2,"EFFECT","NONE",loseMaxHp(intField(event,"maxHpLoss"))); }
                break;
            case "GoldenWing":
                if (start) { selectWithEffects(c,0,"REMOVE_CARD","GRID","purge",1,loseHp(intField(event,"damage"))); deterministic(c,1,"EFFECT","NONE",gainGold(intField(event,"goldAmount"))); leave(c,2); }
                else if ("PURGE".equals(phase)) continueChoice(c,0,"GRID");
                break;
            case "GoopPuddle":
                if (start) { deterministic(c,0,"EFFECT","NONE",gainGold(intField(event,"gold")),loseHp(intField(event,"damage"))); deterministic(c,1,"EFFECT","NONE",loseGold(intField(event,"goldLoss"))); }
                break;
            case "Mushrooms":
                if ("0".equals(phase)) { complex(c,0,"COMBAT","COMBAT",startCombat("The Mushroom Lair")); deterministic(c,1,"EFFECT","NONE",gainHp(Math.max(1,AbstractDungeon.player.maxHealth/4)),addCard("Parasite",1)); }
                else if ("2".equals(phase)) continueChoice(c,0,"COMBAT");
                break;
            case "ScrapOoze":
                if ("0".equals(phase)) {
                    double success=intField(event,"relicObtainChance")/100.0D;
                    stochastic(c,0,"STOCHASTIC","EVENT",outcome(success,loseHp(intField(event,"dmg")),randomRelic("ANY")),outcome(1.0D-success,loseHp(intField(event,"dmg"))));
                    leave(c,1);
                }
                break;
            case "ShiningLight":
                if (start) { complex(c,0,"STOCHASTIC","NONE",loseHp(intField(event,"damage")),randomUpgrade(2)); leave(c,1); }
                break;
            case "Sssserpent":
                if (start) { deterministic(c,0,"EFFECT","NONE",gainGold(intField(event,"goldReward")),addCard("Doubt",1)); leave(c,1); }
                break;
            case "AccursedBlacksmith":
                if ("0".equals(phase)) { select(c,0,"UPGRADE_CARD","GRID","upgrade",1); deterministic(c,1,"EFFECT","NONE",addCard("Pain",1),obtainRelic("WarpedTongs")); leave(c,2); }
                break;
            case "Bonfire":
                if ("INTRO".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("CHOOSE".equals(phase)) complex(c,0,"SELECT_CARD","GRID",targetRule("BONFIRE_OFFER",1));
                break;
            case "PurificationShrine":
                if (start) { select(c,0,"REMOVE_CARD","GRID","purge",1); leave(c,1); }
                break;
            case "UpgradeShrine":
                if (start) { select(c,0,"UPGRADE_CARD","GRID","upgrade",1); leave(c,1); }
                break;
            case "Transmogrifier":
                if (start) { select(c,0,"TRANSFORM_CARD","GRID","transform",1); leave(c,1); }
                break;
            case "Duplicator":
                if ("0".equals(phase)) { select(c,0,"DUPLICATE_CARD","GRID","duplicate",1); leave(c,1); }
                break;
            case "FountainOfCurseRemoval":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",effect("remove_curses","count",-1,"card_ids",removableCurseIds(),"exclude_ids",Arrays.asList("AscendersBane","CurseOfTheBell","Necronomicurse"))); leave(c,1); }
                break;
            case "GoldShrine":
                if (start) { deterministic(c,0,"EFFECT","NONE",gainGold(intField(event,"goldAmt"))); deterministic(c,1,"EFFECT","NONE",gainGold(275),addCard("Regret",1)); leave(c,2); }
                break;
            case "Designer": describeDesigner(event, phase, c); break;
            case "FaceTrader":
                if ("INTRO".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("MAIN".equals(phase)) { deterministic(c,0,"EFFECT","NONE",gainGold(staticIntField(event,"goldReward")),loseHp(staticIntField(event,"damage"))); complex(c,1,"RANDOM_REWARD","NONE",randomRelic("FACE")); leave(c,2); }
                break;
            case "GremlinMatchGame":
                if ("INTRO".equals(phase) || "RULE_EXPLANATION".equals(phase))
                    complex(c,0,"MINI_GAME","EVENT",effect("mini_game","id",name));
                else if ("COMPLETE".equals(phase)) leave(c,0);
                break;
            case "GremlinWheelGame":
                describeGremlinWheel(event, phase, c);
                break;
            case "Lab":
                if (start) complex(c,0,"RANDOM_REWARD","COMBAT_REWARD",effect("random_potion","count",AbstractDungeon.ascensionLevel >= 15 ? 2 : 3));
                break;
            case "Nloth":
                if ("0".equals(phase)) {
                    String reward = AbstractDungeon.player.hasRelic("Nloth's Gift") ? "Circlet" : "Nloth's Gift";
                    if ("Circlet".equals(reward)) { deterministic(c,0,"EFFECT","NONE",obtainRelic(reward)); deterministic(c,1,"EFFECT","NONE",obtainRelic(reward)); }
                    else { tradeRelic(c,0,relicId(event,"choice1"),reward); tradeRelic(c,1,relicId(event,"choice2"),reward); }
                    leave(c,2);
                }
                break;
            case "NoteForYourself":
                if ("INTRO".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("CHOOSE".equals(phase)) { selectWithEffects(c,0,"TRADE_CARD","GRID","purge",1,addCard(cardId(event,"obtainCard"),1)); leave(c,1); }
                break;
            case "WeMeetAgain":
                if (start) { deterministic(c,0,"TRADE_POTION","NONE",effect("lose_potion","id","EVENT_SELECTED"),obtainRelic(relicId(event,"givenRelic"))); deterministic(c,1,"EFFECT","NONE",loseGold(intField(event,"goldAmount")),obtainRelic(relicId(event,"givenRelic"))); deterministic(c,2,"TRADE_CARD","NONE",effect("remove_card","card_id",cardId(event,"cardOption")),obtainRelic(relicId(event,"givenRelic"))); leave(c,3); }
                break;
            case "WomanInBlue":
                if (start) { deterministic(c,0,"EFFECT","COMBAT_REWARD",loseGold(20),effect("random_potion","count",1)); deterministic(c,1,"EFFECT","COMBAT_REWARD",loseGold(30),effect("random_potion","count",2)); deterministic(c,2,"EFFECT","COMBAT_REWARD",loseGold(40),effect("random_potion","count",3)); if (c.size()>3 && AbstractDungeon.ascensionLevel >= 15) deterministic(c,3,"LEAVE","NONE",loseHp(Math.max(1,(int)Math.ceil(AbstractDungeon.player.maxHealth*0.05D)))); else leave(c,3); }
                break;
            case "Addict":
                if ("0".equals(phase)) { complex(c,0,"RANDOM_REWARD","NONE",loseGold(85),randomRelic("ANY")); complex(c,1,"RANDOM_REWARD","NONE",addCard("Shame",1),randomRelic("ANY")); leave(c,2); }
                break;
            case "BackToBasics":
                if (start) { select(c,0,"REMOVE_CARD","GRID","purge",1); deterministic(c,1,"EFFECT","NONE",effect("upgrade_matching","pattern","STARTER_STRIKE_DEFEND")); }
                break;
            case "Beggar":
                if ("INTRO".equals(phase)) { selectWithCost(c,0,"REMOVE_CARD","EVENT","purge",1,75); leave(c,1); }
                else if ("GAVE_MONEY".equals(phase)) select(c,0,"REMOVE_CARD","GRID","purge",1);
                break;
            case "Colosseum":
                if ("INTRO".equals(phase)) complex(c,0,"COMBAT","COMBAT",startCombat("Slavers"));
                else if ("FIGHT".equals(phase)) continueChoice(c,0,"COMBAT");
                else if ("POST_COMBAT".equals(phase)) { complex(c,0,"COMBAT","COMBAT",startCombat("Nob and Taskmaster")); leave(c,1); }
                else continueChoice(c,0,"EVENT");
                break;
            case "CursedTome": describeCursedTome(event, phase, c); break;
            case "DrugDealer":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",addCard("J.A.X.",1)); select(c,1,"TRANSFORM_CARD","GRID","transform",2); deterministic(c,2,"EFFECT","NONE",obtainRelic("MutagenicStrength")); }
                break;
            case "ForgottenAltar":
                if ("0".equals(phase)) { deterministic(c,0,"TRADE_RELIC","NONE",loseRelic("Golden Idol"),obtainRelic("Bloody Idol")); deterministic(c,1,"EFFECT","NONE",gainMaxHp(5),loseHp(intField(event,"hpLoss"))); deterministic(c,2,"EFFECT","NONE",addCard("Decay",1)); }
                break;
            case "Ghosts":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",loseMaxHp(intField(event,"hpLoss")),addCard("Ghostly",AbstractDungeon.ascensionLevel >= 15 ? 3 : 5)); leave(c,1); }
                break;
            case "KnowingSkull": describeKnowingSkull(event, phase, c); break;
            case "MaskedBandits":
                if ("INTRO".equals(phase)) { deterministic(c,0,"EFFECT","NONE",effect("lose_all_gold")); complex(c,1,"COMBAT","COMBAT",startCombat("Masked Bandits")); }
                else continueChoice(c,0,"EVENT");
                break;
            case "Nest":
                if ("0".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("1".equals(phase)) { deterministic(c,0,"EFFECT","NONE",gainGold(intField(event,"goldGain"))); deterministic(c,1,"EFFECT","NONE",loseHp(6),addCard("RitualDagger",1)); }
                break;
            case "TheJoust": describeJoust(phase,c); break;
            case "TheLibrary":
                if ("0".equals(phase)) { complex(c,0,"CARD_REWARD","CARD_REWARD",effect("choose_generated_card","count",1)); deterministic(c,1,"EFFECT","NONE",gainHp(intField(event,"healAmt"))); }
                break;
            case "TheMausoleum":
                if (start) { double cursed=intField(event,"percent")/100.0D; stochastic(c,0,"STOCHASTIC","NONE",outcome(cursed,addCard("Writhe",1),randomRelic("ANY")),outcome(1.0D-cursed,randomRelic("ANY"))); leave(c,1); }
                break;
            case "Vampires":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",loseMaxHp(intField(event,"maxHpLoss")),effect("replace_starter_strikes","card_id","Bite","amount",5)); if (c.size()>2) { deterministic(c,1,"TRADE_RELIC","NONE",loseRelic("Blood Vial"),effect("replace_starter_strikes","card_id","Bite","amount",5)); leave(c,2); } else leave(c,1); }
                break;
            case "Falling": describeFalling(event,phase,c); break;
            case "MindBloom": describeMindBloom(event,phase,c); break;
            case "MoaiHead":
                if ("0".equals(phase)) { deterministic(c,0,"EFFECT","NONE",effect("full_heal"),loseMaxHp(intField(event,"hpAmt"))); deterministic(c,1,"TRADE_RELIC","NONE",loseRelic("Golden Idol"),gainGold(333)); leave(c,2); }
                break;
            case "MysteriousSphere":
                if ("INTRO".equals(phase)) { complex(c,0,"COMBAT","COMBAT",startCombat("2 Orb Walkers"),randomRelic("RARE")); leave(c,1); }
                else if ("PRE_COMBAT".equals(phase)) continueChoice(c,0,"COMBAT");
                else continueChoice(c,0,"MAP");
                break;
            case "SecretPortal":
                if (start) { deterministic(c,0,"NAVIGATION","MAP",effect("jump_to_boss")); leave(c,1); }
                break;
            case "SensoryStone":
                if ("INTRO".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("INTRO_2".equals(phase)) { complex(c,0,"CARD_REWARD","CARD_REWARD",effect("colorless_card_reward","count",1)); complex(c,1,"CARD_REWARD","CARD_REWARD",loseHp(5),effect("colorless_card_reward","count",2)); complex(c,2,"CARD_REWARD","CARD_REWARD",loseHp(10),effect("colorless_card_reward","count",3)); }
                break;
            case "SpireHeart": continueChoice(c,0,"EVENT"); break;
            case "TombRedMask":
                if (start) {
                    deterministic(c,0,"EFFECT","NONE",gainGold(222));
                    if (AbstractDungeon.player.hasRelic("Red Mask")) leave(c,1);
                    else { deterministic(c,1,"TRADE_RELIC","NONE",effect("lose_all_gold"),obtainRelic("Red Mask")); leave(c,2); }
                }
                break;
            case "WindingHalls":
                if ("0".equals(phase)) continueChoice(c,0,"EVENT");
                else if ("1".equals(phase)) { deterministic(c,0,"EFFECT","NONE",loseHp(intField(event,"hpAmt")),addCard("Madness",2)); deterministic(c,1,"EFFECT","NONE",gainHp(intField(event,"healAmt")),addCard("Writhe",1)); deterministic(c,2,"EFFECT","NONE",loseMaxHp(intField(event,"maxHPAmt"))); }
                break;
            default:
                // Registered forced/result phases intentionally carry no state-changing effect.
                break;
        }
        markUnspecifiedLeaves(c);
        for (Map<String, Object> choice : c) {
            if (Boolean.TRUE.equals(choice.get("enabled")) && "UNKNOWN".equals(choice.get("kind"))) {
                throw new IllegalStateException("unmapped enabled choice in phase " + phase);
            }
        }
    }

    private static void describeCursedTome(AbstractEvent e, String phase, List<Map<String,Object>> c) {
        if ("INTRO".equals(phase)) { complex(c,0,"COMMIT","EVENT",effect("commit_reading","unavoidable_hp_loss",6)); leave(c,1); }
        else if ("PAGE_1".equals(phase)) deterministic(c,0,"CONTINUE","EVENT",loseHp(1));
        else if ("PAGE_2".equals(phase)) deterministic(c,0,"CONTINUE","EVENT",loseHp(2));
        else if ("PAGE_3".equals(phase)) deterministic(c,0,"CONTINUE","EVENT",loseHp(3));
        else if ("LAST_PAGE".equals(phase)) { complex(c,0,"RANDOM_REWARD","NONE",loseHp(intField(e,"finalDmg")),randomRelic("BOOK")); deterministic(c,1,"LEAVE","NONE",loseHp(3)); }
        else continueChoice(c,0,"MAP");
    }

    private static void describeDesigner(AbstractEvent e, String phase, List<Map<String,Object>> c) {
        if ("INTRO".equals(phase)) { continueChoice(c,0,"EVENT"); return; }
        if (!"MAIN".equals(phase)) return;
        int adjust=intField(e,"adjustCost"), clean=intField(e,"cleanUpCost"), full=intField(e,"fullServiceCost");
        boolean upgradeOne=boolField(e,"adjustmentUpgradesOne"), removeCards=boolField(e,"cleanUpRemovesCards");
        if (upgradeOne) selectWithCost(c,0,"UPGRADE_CARD","GRID","upgrade",1,adjust);
        else deterministic(c,0,"EFFECT","NONE",loseGold(adjust),randomUpgrade(2));
        selectWithCost(c,1,removeCards?"REMOVE_CARD":"TRANSFORM_CARD","GRID",removeCards?"purge":"transform",removeCards?1:2,clean);
        complex(c,2,"MULTI_SELECT","GRID",loseGold(full),targetRule("PURGE",1),randomUpgrade(1));
        deterministic(c,3,"LEAVE","NONE",loseHp(intField(e,"hpLoss")));
    }

    private static void describeKnowingSkull(AbstractEvent e, String phase, List<Map<String,Object>> c) {
        if ("INTRO_1".equals(phase)) { continueChoice(c,0,"EVENT"); return; }
        if (!"ASK".equals(phase)) return;
        deterministic(c,0,"RANDOM_REWARD","EVENT",loseHp(intField(e,"potionCost")),effect("random_potion","count",1));
        deterministic(c,1,"EFFECT","EVENT",loseHp(intField(e,"goldCost")),gainGold(90));
        complex(c,2,"CARD_REWARD","EVENT",loseHp(intField(e,"cardCost")),effect("random_card_reward","count",1));
        deterministic(c,3,"LEAVE","NONE",loseHp(intField(e,"leaveCost")));
    }

    private static void describeJoust(String phase, List<Map<String,Object>> c) {
        if ("HALT".equals(phase)) continueChoice(c,0,"EVENT");
        else if ("EXPLANATION".equals(phase)) {
            stochastic(c,0,"STOCHASTIC","EVENT",outcome(0.3D,loseGold(50),gainGold(250)),outcome(0.7D,loseGold(50)));
            stochastic(c,1,"STOCHASTIC","EVENT",outcome(0.7D,loseGold(50),gainGold(100)),outcome(0.3D,loseGold(50)));
        } else continueChoice(c,0,"EVENT");
    }

    private static void describeFalling(AbstractEvent e, String phase, List<Map<String,Object>> c) {
        if ("INTRO".equals(phase)) { continueChoice(c,0,"EVENT"); return; }
        if (!"CHOICE".equals(phase)) return;
        deterministic(c,0,"REMOVE_CARD","NONE",effect("remove_card","card_id",cardId(e,"skillCard")));
        deterministic(c,1,"REMOVE_CARD","NONE",effect("remove_card","card_id",cardId(e,"powerCard")));
        deterministic(c,2,"REMOVE_CARD","NONE",effect("remove_card","card_id",cardId(e,"attackCard")));
    }

    private static void describeMindBloom(AbstractEvent e, String phase, List<Map<String,Object>> c) {
        if (!"INTRO".equals(phase)) { continueChoice(c,0,"MAP"); return; }
        complex(c,0,"COMBAT","COMBAT",startCombat("Mind Bloom Boss Battle"),randomRelic("RARE"));
        deterministic(c,1,"EFFECT","NONE",effect("upgrade_all_cards"),obtainRelic("Mark of the Bloom"));
        if (AbstractDungeon.floorNum % 50 <= 40) deterministic(c,2,"EFFECT","NONE",gainGold(999),addCard("Normality",2));
        else deterministic(c,2,"EFFECT","NONE",effect("full_heal"),addCard("Doubt",1));
    }

    private static void describeGremlinWheel(AbstractEvent event, String phase, List<Map<String,Object>> c) {
        if ("INTRO".equals(phase)) {
            int gold = intField(event,"goldAmount");
            int hpLoss = (int)(AbstractDungeon.player.maxHealth *
                ((Number)requiredField(event,"hpLossPercent")).floatValue());
            if (CardGroup.getGroupWithoutBottledCards(
                    AbstractDungeon.player.masterDeck.getPurgeableCards()).size() > 0) {
                stochastic(c,0,"MINI_GAME","EVENT",
                    outcome(1.0D/6.0D,gainGold(gold)),
                    outcome(1.0D/6.0D,randomRelic("ANY")),
                    outcome(1.0D/6.0D,effect("full_heal")),
                    outcome(1.0D/6.0D,addCard("Decay",1)),
                    outcome(1.0D/6.0D,targetRule("PURGE",1)),
                    outcome(1.0D/6.0D,loseHp(hpLoss)));
            } else {
                stochastic(c,0,"MINI_GAME","EVENT",
                    outcome(1.0D/6.0D,gainGold(gold)),
                    outcome(1.0D/6.0D,randomRelic("ANY")),
                    outcome(1.0D/6.0D,effect("full_heal")),
                    outcome(1.0D/6.0D,addCard("Decay",1)),
                    outcome(1.0D/6.0D),
                    outcome(1.0D/6.0D,loseHp(hpLoss)));
            }
            return;
        }
        if ("SPIN".equals(phase)) return;
        if ("LEAVE".equals(phase)) { leave(c,0); return; }
        if (!"COMPLETE".equals(phase)) return;

        int result = intField(event,"result");
        switch (result) {
            case 0: deterministic(c,0,"CONTINUE","EVENT"); break;
            case 1: complex(c,0,"RANDOM_REWARD","COMBAT_REWARD",randomRelic("ANY")); break;
            case 2: deterministic(c,0,"EFFECT","EVENT",effect("full_heal")); break;
            case 3: deterministic(c,0,"EFFECT","EVENT",addCard("Decay",1)); break;
            case 4:
                if (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()).size() > 0)
                    select(c,0,"REMOVE_CARD","GRID","purge",1);
                else deterministic(c,0,"CONTINUE","EVENT");
                break;
            default:
                deterministic(c,0,"EFFECT","EVENT",loseHp(
                    (int)(AbstractDungeon.player.maxHealth * ((Number)requiredField(event,"hpLossPercent")).floatValue())));
                break;
        }
    }

    private static void select(List<Map<String,Object>> c,int i,String kind,String follow,String purpose,int count) {
        if ("transform".equalsIgnoreCase(purpose)) deterministic(c,i,kind,follow,targetRule("TRANSFORM",count),effect("random_transform","count",count));
        else deterministic(c,i,kind,follow,targetRule(purpose.toUpperCase(),count));
    }
    private static void selectWithCost(List<Map<String,Object>> c,int i,String kind,String follow,String purpose,int count,int cost) {
        if ("transform".equalsIgnoreCase(purpose)) deterministic(c,i,kind,follow,loseGold(cost),targetRule("TRANSFORM",count),effect("random_transform","count",count));
        else deterministic(c,i,kind,follow,loseGold(cost),targetRule(purpose.toUpperCase(),count));
    }
    private static void selectWithEffects(List<Map<String,Object>> c,int i,String kind,String follow,String purpose,int count,Map<String,Object>... effects) {
        List<Map<String,Object>> all=new ArrayList<>(Arrays.asList(effects)); all.add(targetRule(purpose.toUpperCase(),count));
        deterministic(c,i,kind,follow,all.toArray(new Map[0]));
    }
    private static void tradeRelic(List<Map<String,Object>> c,int i,String lose,String gain) { deterministic(c,i,"TRADE_RELIC","NONE",loseRelic(lose),obtainRelic(gain)); }
    private static void leave(List<Map<String,Object>> c,int i) { deterministic(c,i,"LEAVE","MAP"); }
    private static void continueChoice(List<Map<String,Object>> c,int i,String follow) { deterministic(c,i,"CONTINUE",follow); }
    private static void complex(List<Map<String,Object>> c,int i,String kind,String follow,Map<String,Object>... effects) { set(c,i,kind,follow,effects); }
    private static void deterministic(List<Map<String,Object>> c,int i,String kind,String follow,Map<String,Object>... effects) { set(c,i,kind,follow,effects); }
    private static void set(List<Map<String,Object>> c,int i,String kind,String follow,Map<String,Object>... effects) {
        if (i<0 || i>=c.size()) return;
        Map<String,Object> choice=c.get(i); choice.put("kind",kind); choice.put("followup",follow);
        Map<String,Object> outcome=new LinkedHashMap<>(); outcome.put("probability",1.0D); outcome.put("effects",Arrays.asList(effects));
        choice.put("outcomes",Collections.singletonList(outcome));
    }
    @SafeVarargs
    private static void stochastic(List<Map<String,Object>> c,int i,String kind,String follow,Map<String,Object>... outcomes) {
        if (i<0 || i>=c.size()) return;
        Map<String,Object> choice=c.get(i); choice.put("kind",kind); choice.put("followup",follow); choice.put("outcomes",Arrays.asList(outcomes));
    }
    private static Map<String,Object> outcome(double probability,Map<String,Object>... effects) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("probability",probability); result.put("effects",Arrays.asList(effects)); return result;
    }

    private static Map<String,Object> effect(String type,Object... kv) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("type",type);
        for(int i=0;i+1<kv.length;i+=2) if(kv[i+1]!=null) result.put(String.valueOf(kv[i]),kv[i+1]);
        return result;
    }
    private static Map<String,Object> gainHp(int n){return effect("gain_hp","amount",n);} private static Map<String,Object> loseHp(int n){return effect("lose_hp","amount",n);}
    private static Map<String,Object> gainMaxHp(int n){return effect("gain_max_hp","amount",n);} private static Map<String,Object> loseMaxHp(int n){return effect("lose_max_hp","amount",n);}
    private static Map<String,Object> gainGold(int n){return effect("gain_gold","amount",n);} private static Map<String,Object> loseGold(int n){return effect("lose_gold","amount",n);}
    private static Map<String,Object> addCard(String id,int n){return effect("add_card","card_id",id,"amount",n);} private static Map<String,Object> obtainRelic(String id){return effect("obtain_relic","relic_id",id);}
    private static Map<String,Object> loseRelic(String id){return effect("lose_relic","relic_id",id);} private static Map<String,Object> randomRelic(String pool){return effect("random_relic","pool",pool);}
    private static Map<String,Object> randomUpgrade(int n){return effect("random_upgrade","amount",n);} private static Map<String,Object> startCombat(String id){return effect("start_combat","encounter",id);}
    private static Map<String,Object> chance(String result,int pct){return effect("chance","result",result,"percent",pct);} private static Map<String,Object> rangeEffect(String type,int min,int max){return effect(type,"min",min,"max",max);}
    private static Map<String,Object> targetRule(String purpose,int count){return effect("select_cards","purpose",purpose,"count",count);}

    private static int enabledCount(List<Map<String,Object>> c){int n=0;for(Map<String,Object>x:c)if(Boolean.TRUE.equals(x.get("enabled")))n++;return n;}
    private static boolean hasComplexEffect(List<Map<String,Object>> c){
        Set<String> complex=new HashSet<>(Arrays.asList("chance","random_relic","random_potion","random_card_reward","colorless_card_reward","start_combat","mini_game","choose_generated_card","random_transform","future_cost_choice","commit_reading"));
        for(Map<String,Object> choice:c) for(Map<String,Object> o:(List<Map<String,Object>>)choice.get("outcomes")) for(Map<String,Object> e:(List<Map<String,Object>>)o.get("effects")) if(complex.contains(e.get("type")))return true;
        return false;
    }
    private static void markUnspecifiedLeaves(List<Map<String,Object>> c){
        if (enabledCount(c) != 1) return;
        for(Map<String,Object>x:c) if("UNKNOWN".equals(x.get("kind")) && Boolean.TRUE.equals(x.get("enabled"))) {
            x.put("kind","CONTINUE"); Map<String,Object> o=new LinkedHashMap<>();o.put("probability",1.0D);o.put("effects",Collections.emptyList());x.put("outcomes",Collections.singletonList(o));x.put("followup","EVENT");
        }
    }
    private static boolean isStart(String phase){return "INTRO".equals(phase)||"0".equals(phase)||"HALT".equals(phase)||"INTRO_1".equals(phase);}

    private static String readPhase(Object event){
        if ("GremlinWheelGame".equals(event.getClass().getSimpleName())) {
            Object screen = fieldOrNull(event,"screen");
            if ("INTRO".equals(String.valueOf(screen)) &&
                (Boolean.TRUE.equals(fieldOrNull(event,"startSpin")) ||
                 Boolean.TRUE.equals(fieldOrNull(event,"finishSpin")) ||
                 Boolean.TRUE.equals(fieldOrNull(event,"doneSpinning")))) return "SPIN";
        }
        for(String name:new String[]{"screen","screenNum","curScreen"}) { Object v=fieldOrNull(event,name); if(v!=null)return String.valueOf(v); }
        return "UNKNOWN";
    }
    private static Object fieldOrNull(Object target,String name){
        Class<?> type=target.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception ignored){type=type.getSuperclass();}}
        return null;
    }
    private static Object requiredField(Object target,String name){Object value=fieldOrNull(target,name);if(value==null)throw new IllegalStateException("missing field "+name);return value;}
    private static int intField(Object target,String name){return ((Number)requiredField(target,name)).intValue();}
    private static int staticIntField(Object target,String name){return intField(target,name);}
    private static boolean boolField(Object target,String name){return (Boolean)requiredField(target,name);}
    private static String cardId(Object target,String name){Object v=fieldOrNull(target,name);return v instanceof AbstractCard?((AbstractCard)v).cardID:null;}
    private static String relicId(Object target,String name){Object v=fieldOrNull(target,name);return v instanceof AbstractRelic?((AbstractRelic)v).relicId:null;}
    private static List<String> removableCurseIds(){
        List<String> result=new ArrayList<>();
        Set<String> excluded=new HashSet<>(Arrays.asList("AscendersBane","CurseOfTheBell","Necronomicurse"));
        for(AbstractCard card:AbstractDungeon.player.masterDeck.group) {
            if(card.type==AbstractCard.CardType.CURSE && !card.inBottleFlame && !card.inBottleLightning && !excluded.contains(card.cardID)) result.add(card.cardID);
        }
        return result;
    }
    private static String invokeString(Object target,String name){
        Class<?> type=target.getClass();
        while(type!=null){try{java.lang.reflect.Method method=type.getDeclaredMethod(name);method.setAccessible(true);return String.valueOf(method.invoke(target));}catch(Exception ignored){type=type.getSuperclass();}}
        throw new IllegalStateException("missing method "+name);
    }
}
