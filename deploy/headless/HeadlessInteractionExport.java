import com.example.communicationmod.EventStateExtractor;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.events.RoomEventDialog;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only structured facts. There is deliberately no dispatcher/executor. */
public final class HeadlessInteractionExport {
    public static final String SCHEMA = "headless-interaction-v1";
    private HeadlessInteractionExport() {}

    public static Map<String, Object> object(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    public static List<Object> list(Object... values) { return new ArrayList<>(Arrays.asList(values)); }

    // Gson 2.8.8/JDK 21: never reflect into private Collections$SingletonList.
    public static Object wireTree(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object key : ((Map<?, ?>) value).keySet()) result.put(String.valueOf(key), wireTree(((Map<?, ?>) value).get(key)));
            return result;
        }
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Iterable<?>) value) result.add(wireTree(item));
            return result;
        }
        return value;
    }

    private static Object field(Object target, String name) {
        if (target == null) throw new IllegalStateException("Missing object for " + name);
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch (NoSuchFieldException e) { /* Check superclass. */ }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        throw new IllegalStateException("Missing field " + name);
    }

    private static boolean flag(Object target, String name) { return (Boolean) field(target, name); }
    private static int number(Object target, String name) { return ((Number) field(target, name)).intValue(); }

    private static List<Object> rewardRows() {
        List<RewardItem> rewards = AbstractDungeon.combatRewardScreen.rewards;
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < rewards.size(); i++) {
            RewardItem r = rewards.get(i);
            Map<String, Object> row = object("native_index", i, "type", r.type.name());
            if (r.type == RewardItem.RewardType.GOLD || r.type == RewardItem.RewardType.STOLEN_GOLD) row.put("amount", r.goldAmt + r.bonusGold);
            if (r.relic != null) row.put("relic_id", r.relic.relicId);
            if (r.potion != null) row.put("potion_id", r.potion.ID);
            if (r.relicLink != null) row.put("linked_reward_index", rewards.indexOf(r.relicLink));
            if (r.type == RewardItem.RewardType.CARD) {
                List<Object> uuids = new ArrayList<>(); for (AbstractCard c : r.cards) uuids.add(c.uuid.toString());
                row.put("card_uuids", uuids);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String relicSelectionPurpose() {
        AbstractEvent event=AbstractDungeon.getCurrRoom().event;
        if(event!=null && event.getClass().getSimpleName().equals("Duplicator")
                && number(event,"screenNum")==2 && number(AbstractDungeon.gridSelectScreen,"numCards")==1)
            return "duplicate";
        if(event!=null && event.getClass().getSimpleName().equals("Bonfire")
                && String.valueOf(field(event,"screen")).equals("CHOOSE") && flag(event,"cardSelect")
                && number(AbstractDungeon.gridSelectScreen,"numCards")==1)
            return "bonfire_offer";
        if(event!=null && event.getClass().getSimpleName().equals("BackToBasics")
                && String.valueOf(field(event,"screen")).equals("COMPLETE")
                && number(AbstractDungeon.gridSelectScreen,"numCards")==1)
            return "purge"; // Its original four-argument open overload leaves forPurge=false.
        if(event!=null && event.getClass().getSimpleName().equals("DrugDealer")
                && number(event,"screenNum")==1 && !flag(event,"cardsSelected")
                && number(AbstractDungeon.gridSelectScreen,"numCards")==2)
            return "transform"; // The source opens a generic two-card grid, then transforms in update().
        if(event!=null && event.getClass().getSimpleName().equals("TheLibrary")
                && number(event,"screenNum")==1 && flag(event,"pickCard")
                && number(AbstractDungeon.gridSelectScreen,"numCards")==1
                && AbstractDungeon.gridSelectScreen.targetGroup.size()==20)
            return "obtain";
        if (AbstractDungeon.actionManager.currentAction != null) {
            String action = AbstractDungeon.actionManager.currentAction.getClass().getSimpleName();
            if (action.equals("MeditateAction")) {
                var source = AbstractDungeon.actionManager.currentAction;
                var grid = AbstractDungeon.gridSelectScreen;
                if (field(source, "player") == AbstractDungeon.player && grid.targetGroup == AbstractDungeon.player.discardPile
                        && !flag(source,"optional") && number(source,"numberOfCards") == number(grid,"numCards"))
                    return "meditate";
            }
            if(action.equals("BetterDrawPileToHandAction") && AbstractDungeon.player.cardInUse!=null && "Seek".equals(AbstractDungeon.player.cardInUse.cardID))return "seek";
            if(action.equals("OmniscienceAction") && AbstractDungeon.player.cardInUse!=null && "Omniscience".equals(AbstractDungeon.player.cardInUse.cardID))return "omniscience";
            if(AbstractDungeon.player.cardInUse!=null) {
                String source=AbstractDungeon.player.cardInUse.cardID;
                if(action.equals("AttackFromDeckToHandAction") && source.equals("Secret Weapon"))return "secret_weapon";
                if(action.equals("SkillFromDeckToHandAction") && source.equals("Secret Technique"))return "secret_technique";
            }
            if(action.equals("BetterDiscardPileToHandAction") && AbstractDungeon.player.cardInUse!=null && "Hologram".equals(AbstractDungeon.player.cardInUse.cardID) &&
                    Boolean.FALSE.equals(field(AbstractDungeon.actionManager.currentAction,"setCost")))return "hologram";
            if (action.equals("ScryAction")) return "scry";
            if (action.equals("DiscardPileToTopOfDeckAction")) return "topdeck";
            if (action.equals("ExhumeAction")) return "retrieve_exhaust";
            if (action.equals("BetterDiscardPileToHandAction") && Boolean.TRUE.equals(field(AbstractDungeon.actionManager.currentAction,"setCost"))
                    && number(AbstractDungeon.actionManager.currentAction,"newCost")==0
                    && Boolean.FALSE.equals(field(AbstractDungeon.actionManager.currentAction,"optional")))return "liquid_memories";
        }
        if (AbstractDungeon.getCurrRoom().event != null && AbstractDungeon.getCurrRoom().event.getClass().getSimpleName().equals("NeowEvent")) {
            for (Object reward : (Iterable<?>) field(AbstractDungeon.getCurrRoom().event, "rewards")) {
                if (!flag(reward, "activated")) continue;
                String type = String.valueOf(field(reward, "type"));
                if (type.startsWith("REMOVE")) return "purge";
                if (type.startsWith("TRANSFORM")) return "transform";
                if (type.equals("UPGRADE_CARD")) return "upgrade";
            }
        }
        for (var relic : AbstractDungeon.player.relics) {
            String name = relic.getClass().getSimpleName();
            if ((name.equals("BottledFlame") || name.equals("BottledLightning") || name.equals("BottledTornado")) && !flag(relic, "cardSelected")) return "bottle";
            if (name.equals("DollysMirror") && !flag(relic, "cardSelected")) return "duplicate";
            if (name.equals("Astrolabe") && !flag(relic, "cardsSelected")) return "transform";
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> eventView(AbstractEvent event, ArrayList<LargeDialogOptionButton> buttons) {
        return (Map<String, Object>) wireTree(EventStateExtractor.extract(event, buttons));
    }

    public static Map<String, Object> shopCard(AbstractCard card, String slot, int nativeIndex, boolean onSale) {
        return object("slot_id", slot, "kind", "card", "id", card.cardID, "native_index", nativeIndex,
                "price", card.price, "upgrades", card.timesUpgraded, "card_type", card.type.name(), "sold_out", false, "on_sale", onSale);
    }

    private static Map<String, Object> effect(String type, Object... fields) {
        Map<String, Object> result = object(fields); result.put("type", type); return result;
    }

    private static Map<String,Object> starterUpgradeEffect() {
        List<Object> ids=list();
        for(AbstractCard card:AbstractDungeon.player.masterDeck.group)
            if((card.hasTag(AbstractCard.CardTags.STARTER_STRIKE) || card.hasTag(AbstractCard.CardTags.STARTER_DEFEND))
                    && !ids.contains(card.cardID))ids.add(card.cardID);
        return effect("upgrade_matching","pattern","STARTER_STRIKE_DEFEND","card_ids",ids);
    }

    /** Entry-time offers are already visible; the reward is read only after activation. */
    private static Map<String,Object> weMeetFacts(AbstractEvent event) {
        String phase=String.valueOf(field(event,"screen"));
        Map<String,Object> facts=object("schema","headless-wemeetagain-v1","phase",phase);
        if(phase.equals("INTRO")) {
            Map<String,Object> costs=object();
            var potion=(com.megacrit.cardcrawl.potions.AbstractPotion)field(event,"potionOption");
            int slot=AbstractDungeon.player.potions.indexOf(potion);
            if(potion!=null && slot>=0)costs.put("0",object("kind","potion","slot",slot,"id",potion.ID));
            int gold=number(event,"goldAmount");
            if(gold>0)costs.put("1",object("kind","gold","amount",gold));
            AbstractCard card=(AbstractCard)field(event,"cardOption");
            if(card!=null && AbstractDungeon.player.masterDeck.group.contains(card))
                costs.put("2",object("kind","card","uuid",card.uuid.toString(),"id",card.cardID));
            facts.put("costs",costs);
        } else if(phase.equals("COMPLETE")) {
            var relic=(com.megacrit.cardcrawl.relics.AbstractRelic)field(event,"givenRelic");
            facts.put("reward_relic",relic==null?null:object("id",relic.relicId,"counter",relic.counter));
        } else throw new IllegalStateException("Unknown WeMeetAgain phase");
        return facts;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> weMeetEvent(Map<String,Object> facts, ArrayList<LargeDialogOptionButton> buttons) {
        String phase=String.valueOf(facts.get("phase"));
        List<Object> choices=list();int enabled=0;
        for(int i=0;i<buttons.size();i++) {
            LargeDialogOptionButton button=buttons.get(i);
            List<Object> effects=list();String kind="CONTINUE";
            if(phase.equals("INTRO")) {
                kind=i==0?"TRADE_POTION":i==1?"EFFECT":i==2?"TRADE_CARD":"LEAVE";
                if(i<3 && !button.isDisabled) {
                    Map<String,Object> cost=(Map<String,Object>)((Map<?,?>)facts.get("costs")).get(String.valueOf(i));
                    if(cost==null)throw new IllegalStateException("Enabled WeMeetAgain offer has no native cost");
                    if(i==0)effects.add(effect("lose_potion","slot",cost.get("slot"),"id",cost.get("id")));
                    else if(i==1)effects.add(effect("lose_gold","amount",cost.get("amount")));
                    else effects.add(effect("remove_card","card_id",cost.get("id"),"id",cost.get("uuid")));
                    effects.add(effect("random_relic","pool","ANY","count",1));
                }
            }
            choices.add(object("button_index",i,"action_index",button.isDisabled?null:enabled++,
                    "enabled",!button.isDisabled,"label",button.msg,"kind",kind,"followup","EVENT",
                    "outcomes",list(object("probability",1.0,"effects",effects))));
        }
        return object("id","WeMeetAgain","class_name","WeMeetAgain","phase",phase,
                "semantics_status","KNOWN","decision_kind",phase.equals("INTRO")?"COMPLEX":"FORCED","choices",choices);
    }

    /** Describe the inclusive source RNG range without rolling it or reading goldAmount before activation. */
    public static List<Object> goldenWingGoldOutcomes(int minimum, int maximum) {
        if (minimum < 0 || maximum < minimum || maximum - minimum > 1000)
            throw new IllegalArgumentException("Invalid GoldenWing gold range");
        List<Object> outcomes = new ArrayList<>();
        for (int amount = minimum; amount <= maximum; amount++)
            outcomes.add(object("probability", 1.0 / (maximum - minimum + 1),
                    "effects", list(effect("gain_gold", "amount", amount))));
        return outcomes;
    }

    /** Wheel result is deliberately absent until the original preApplyResult has run. */
    public static Map<String,Object> wheelFacts(String nativePhase, boolean start, boolean finish,
            boolean done, boolean pressed, int gold, int damage, boolean purgeAvailable, Integer result) {
        String stage = nativePhase.equals("INTRO") && (start || finish || done) ? "SPIN" : nativePhase;
        Map<String,Object> facts = object("native_phase",nativePhase,"stage",stage,
                "start_spin",start,"finish_spin",finish,"done_spinning",done,"button_pressed",pressed,
                "gold_amount",gold,"hp_loss",damage,"purge_available",purgeAvailable);
        if(stage.equals("COMPLETE") || stage.equals("LEAVE")) {
            if(result == null || result < 0 || result > 5) throw new IllegalStateException("Invalid visible wheel result");
            facts.put("result",result);
        }
        return facts;
    }

    private static Map<String,Object> wheelFacts(AbstractEvent event) {
        String nativePhase=String.valueOf(field(event,"screen"));
        Integer result=nativePhase.equals("COMPLETE") || nativePhase.equals("LEAVE") ? number(event,"result") : null;
        return wheelFacts(nativePhase,flag(event,"startSpin"),flag(event,"finishSpin"),flag(event,"doneSpinning"),
                flag(event,"buttonPressed"),number(event,"goldAmount"),
                (int)(AbstractDungeon.player.maxHealth*((Number)field(event,"hpLossPercent")).floatValue()),
                com.megacrit.cardcrawl.cards.CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards()).size()>0,result);
    }

    /** Pure descriptor for the source wheel's two-button spin and deferred result claim. */
    public static Map<String,Object> wheelEvent(Map<String,Object> facts, String label) {
        String stage=String.valueOf(facts.get("stage")),kind="CONTINUE",follow="EVENT";
        List<Object> effects=list(),outcomes=null;
        if(stage.equals("SPIN")) {
            // Only gold is awarded in preApplyResult; all other results are claimed later.
            outcomes=list(object("probability",1.0/6,"effects",list(effect("gain_gold","amount",facts.get("gold_amount")))),
                    object("probability",5.0/6,"effects",list()));
        } else if(stage.equals("COMPLETE")) {
            int result=((Number)facts.get("result")).intValue();
            if(result==1) {kind="RANDOM_REWARD";follow="COMBAT_REWARD";effects=list(effect("relic_reward","pool","ANY"));}
            else if(result==2) {kind="EFFECT";effects=list(effect("full_heal"));}
            else if(result==3) {kind="EFFECT";effects=list(effect("add_card","card_id","Decay","amount",1));}
            else if(result==4 && Boolean.TRUE.equals(facts.get("purge_available"))) {
                kind="REMOVE_CARD";follow="GRID";effects=list(effect("select_cards","purpose","PURGE","count",1));
            } else if(result==5) {kind="EFFECT";effects=list(effect("lose_hp","amount",facts.get("hp_loss")));}
        } else if(stage.equals("LEAVE")) {kind="LEAVE";follow="MAP";}
        else if(!stage.equals("INTRO")) throw new IllegalStateException("Unknown wheel stage");
        if(outcomes==null)outcomes=list(object("probability",1.0,"effects",effects));
        return object("id","Wheel of Change","class_name","GremlinWheelGame","phase",stage,
                "semantics_status","KNOWN","decision_kind","FORCED","choices",list(object(
                "button_index",0,"action_index",0,"enabled",true,"label",label,
                "kind",kind,"followup",follow,"outcomes",outcomes)));
    }

    /** Pure Neow reward descriptor; never constructs/activates a NeowReward. */
    public static Map<String, Object> neowChoice(String type, String drawback, int bonus, int hp, String starter) {
        List<Object> effects = new ArrayList<>();
        String follow = "NONE";
        switch (drawback) {
            case "TEN_PERCENT_HP_LOSS": effects.add(effect("lose_max_hp", "amount", bonus)); break;
            case "NO_GOLD": effects.add(effect("lose_all_gold")); break;
            case "CURSE": effects.add(effect("random_curse", "count", 1)); break;
            case "PERCENT_DAMAGE": effects.add(effect("lose_hp", "amount", hp / 10 * 3)); break;
            case "NONE": break;
            default: throw new IllegalStateException("Unknown Neow drawback");
        }
        switch (type) {
            case "THREE_CARDS": case "THREE_RARE_CARDS":
                effects.add(effect("random_card_reward", "count", 3)); follow = "CARD_REWARD"; break;
            case "RANDOM_COLORLESS": case "RANDOM_COLORLESS_2":
                effects.add(effect("colorless_card_reward", "count", 3)); follow = "CARD_REWARD"; break;
            case "ONE_RANDOM_RARE_CARD": effects.add(effect("random_card", "pool", "RARE", "count", 1)); break;
            case "REMOVE_CARD": case "REMOVE_TWO": case "UPGRADE_CARD": case "TRANSFORM_CARD": case "TRANSFORM_TWO_CARDS":
                String purpose = type.startsWith("REMOVE") ? "PURGE" : type.equals("UPGRADE_CARD") ? "UPGRADE" : "TRANSFORM";
                effects.add(effect("select_cards", "purpose", purpose, "count", type.equals("REMOVE_TWO") || type.equals("TRANSFORM_TWO_CARDS") ? 2 : 1));
                follow = "GRID"; break;
            case "THREE_SMALL_POTIONS": effects.add(effect("random_potion", "count", 3)); break;
            case "RANDOM_COMMON_RELIC": effects.add(effect("random_relic", "pool", "COMMON")); break;
            case "ONE_RARE_RELIC": effects.add(effect("random_relic", "pool", "RARE")); break;
            case "TEN_PERCENT_HP_BONUS": effects.add(effect("gain_max_hp", "amount", bonus)); break;
            case "TWENTY_PERCENT_HP_BONUS": effects.add(effect("gain_max_hp", "amount", bonus * 2)); break;
            case "HUNDRED_GOLD": effects.add(effect("gain_gold", "amount", 100)); break;
            case "TWO_FIFTY_GOLD": effects.add(effect("gain_gold", "amount", 250)); break;
            case "THREE_ENEMY_KILL": effects.add(effect("obtain_relic", "relic_id", "NeowsBlessing")); break;
            case "BOSS_RELIC":
                effects.add(effect("lose_relic", "relic_id", starter)); effects.add(effect("random_relic", "pool", "BOSS")); break;
            default: throw new IllegalStateException("Unknown Neow reward");
        }
        return object("kind", "EFFECT", "followup", follow, "outcomes", list(object("probability", 1.0, "effects", effects)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> neow(AbstractEvent event, ArrayList<LargeDialogOptionButton> buttons) {
        int phase = number(event, "screenNum"), enabled = 0;
        List<NeowReward> rewards = (List<NeowReward>) field(event, "rewards");
        List<Object> choices = new ArrayList<>();
        boolean known = phase == 0 || phase == 1 || phase == 2 || phase == 3 || phase == 99;
        for (int i = 0; i < buttons.size(); i++) {
            LargeDialogOptionButton b = buttons.get(i);
            Map<String, Object> c = object("button_index", i, "action_index", b.isDisabled ? null : enabled++,
                    "enabled", !b.isDisabled, "label", b.msg, "kind", "UNKNOWN", "followup", "NONE", "outcomes", new ArrayList<>());
            if (phase == 3 && i < rewards.size()) {
                NeowReward reward = rewards.get(i);
                c.putAll(neowChoice(reward.type.name(), reward.drawback.name(), number(reward, "hp_bonus"),
                        AbstractDungeon.player.currentHealth, AbstractDungeon.player.relics.isEmpty() ? "" : AbstractDungeon.player.relics.get(0).relicId));
            } else if (known) {
                c.put("kind", phase == 99 || phase == 2 && i == 1 ? "LEAVE" : "CONTINUE");
                c.put("followup", phase == 99 || phase == 2 && i == 1 ? "MAP" : "EVENT");
                c.put("outcomes", list(object("probability", 1.0, "effects", new ArrayList<>())));
            }
            choices.add(c);
        }
        return object("id", "Neow", "class_name", "NeowEvent", "phase", String.valueOf(phase),
                "semantics_status", known ? "KNOWN" : "UNKNOWN", "decision_kind", "COMPLEX", "choices", choices);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> capture(GameKernel kernel) {
        Map<String, Object> out = HeadlessStateExport.capture(kernel);
        if (AbstractDungeon.player == null) return out;
        String phase = String.valueOf(out.get("phase"));
        Map<String, Object> menu = object("schema", SCHEMA);
        out.put("interaction", menu);
        AbstractEvent currentEvent=AbstractDungeon.getCurrRoom().event;
        if(HeadlessFlowKernel.isNeowAstrolabeGrid()) {
            var pools=List.of(AbstractDungeon.commonCardPool,AbstractDungeon.srcUncommonCardPool,
                AbstractDungeon.srcRareCardPool,AbstractDungeon.colorlessCardPool);
            var keys=List.of("common","uncommon","rare","colorless");
            Map<String,Object> catalog=object(), colors=object();
            for(int i=0;i<pools.size();i++) {
                List<String> ids=new ArrayList<>();
                for(AbstractCard card:pools.get(i).group)ids.add(card.cardID);
                catalog.put(keys.get(i),ids);
            }
            for(AbstractCard card:AbstractDungeon.gridSelectScreen.targetGroup.group)colors.put(card.uuid.toString(),card.color.name());
            // CardLibrary.getCurse iterates this same HashMap (excluding protected curses).
            List<String> curses=new ArrayList<>();
            try {
                Field source=com.megacrit.cardcrawl.helpers.CardLibrary.class.getDeclaredField("curses");
                source.setAccessible(true);
                for(Object key:((Map<?,?>)source.get(null)).keySet()) {
                    String id=(String)key;
                    if(!List.of("AscendersBane","Necronomicurse","CurseOfTheBell","Pride").contains(id))curses.add(id);
                }
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            catalog.put("curse",curses);
            menu.put("astrolabe",object("catalog",catalog,"colors",colors));
        }
        if(currentEvent instanceof com.megacrit.cardcrawl.events.city.TheJoust) {
            String stage=String.valueOf(field(currentEvent,"screen"));
            if(List.of("PRE_JOUST","JOUST","COMPLETE").contains(stage))
                menu.put("joust_bet_owner",flag(currentEvent,"betFor"));
        }
        if(currentEvent instanceof com.megacrit.cardcrawl.events.city.Colosseum)
            menu.put("colosseum_potion_modifier",com.megacrit.cardcrawl.rooms.AbstractRoom.blizzardPotionMod);
        if (currentEvent!=null && currentEvent.getClass().getSimpleName().equals("NeowEvent")
                && phase.equals("REWARD") && AbstractDungeon.player.hasRelic("Tiny House")) {
            // Catalog order, not unrevealed reward order. Only exported after the swap.
            Map<String,Object> pools=object();
            var groups=List.of(AbstractDungeon.commonCardPool,AbstractDungeon.uncommonCardPool,AbstractDungeon.rareCardPool);
            String[] tiers={"common","uncommon","rare"};
            for(int i=0;i<groups.size();i++) {
                List<String> cards=new ArrayList<>();
                for(AbstractCard card:groups.get(i).group)cards.add(card.cardID);
                pools.put(tiers[i],cards);
            }
            menu.put("neow_card_catalog",pools);
        }
        if(currentEvent!=null && currentEvent.getClass().getSimpleName().equals("Designer")) {
            String stage=String.valueOf(field(currentEvent,"curScreen"));
            Map<String,Object> facts=object("phase",stage);
            if(stage.equals("MAIN")) {
                facts.put("upgrade_one",flag(currentEvent,"adjustmentUpgradesOne"));
                facts.put("remove_one",flag(currentEvent,"cleanUpRemovesCards"));
            }
            menu.put("designer",facts);
        }
        if(currentEvent!=null && currentEvent.getClass().getSimpleName().equals("DeadAdventurer")) {
            var awarded=(com.megacrit.cardcrawl.relics.AbstractRelic)field(currentEvent,"relicRewardMetric");
            menu.put("dead_search_receipt",object("count",number(currentEvent,"numRewards"),"chance",number(currentEvent,"encounterChance"),
                    "relic_id",awarded==null?null:awarded.relicId));
        }
        if(currentEvent!=null && currentEvent.getClass().getSimpleName().equals("GremlinWheelGame"))
            menu.put("wheel",wheelFacts(currentEvent));
        if(currentEvent!=null && currentEvent.getClass().getSimpleName().equals("WeMeetAgain"))
            menu.put("we_meet_again",weMeetFacts(currentEvent));
        Object chest = null;
        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoom)
            chest = ((com.megacrit.cardcrawl.rooms.TreasureRoom) AbstractDungeon.getCurrRoom()).chest;
        else if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoomBoss)
            chest = ((com.megacrit.cardcrawl.rooms.TreasureRoomBoss) AbstractDungeon.getCurrRoom()).chest;
        if (chest != null) {
            String tier = String.valueOf(field(chest, "relicReward"));
            menu.put("chest", object("boss", AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.TreasureRoomBoss,
                    "open", flag(chest, "isOpen"), "gold_reward", flag(chest, "goldReward"),
                    "gold_amount", number(chest, "GOLD_AMT"), "relic_tier", tier.equals("NONE") || tier.equals("null") ? null : tier.toLowerCase(java.util.Locale.ROOT)));
        }
        if (phase.equals("EVENT") || phase.equals("NEOW")) {
            AbstractEvent event = AbstractDungeon.getCurrRoom().event;
            ArrayList<LargeDialogOptionButton> buttons = !RoomEventDialog.optionList.isEmpty()
                    ? RoomEventDialog.optionList : event.imageEventText.optionList;
            Map<String, Object> view = menu.containsKey("wheel") ? wheelEvent((Map<String,Object>)menu.get("wheel"),buttons.isEmpty()?"wheel control":buttons.get(0).msg)
                    : menu.containsKey("we_meet_again") ? weMeetEvent((Map<String,Object>)menu.get("we_meet_again"),buttons)
                    : event.getClass().getSimpleName().equals("NeowEvent") ? neow(event, buttons) : eventView(event, buttons);
            if (event.getClass().getSimpleName().equals("GremlinMatchGame")) {
                menu.put("match_game", HeadlessMatchGame.capture(event));
                if (view.get("phase").equals("INTRO") || view.get("phase").equals("RULE_EXPLANATION"))
                    for (Object row : (List<?>) view.get("choices")) {
                        Map<String,Object> choice = (Map<String,Object>) row;
                        choice.put("kind", "CONTINUE");
                        choice.put("outcomes", list(object("probability", 1.0, "effects", list())));
                    }
            }
            menu.put("event", view);
            Map<String, Object> transitions = new LinkedHashMap<>();
            String name = event.getClass().getSimpleName(), at = String.valueOf(view.get("phase"));
            if(name.equals("TheLibrary") && at.equals("0")) {
                Map<String,Object> draft=object("offset",AbstractDungeon.cardBlizzRandomizer);
                List<String> tiers=List.of("common","uncommon","rare");
                var pools=List.of(AbstractDungeon.commonCardPool,AbstractDungeon.uncommonCardPool,AbstractDungeon.rareCardPool);
                for(int j=0;j<tiers.size();++j) {
                    List<Object> ids=new ArrayList<>();
                    for(AbstractCard card:pools.get(j).group)ids.add(card.cardID);
                    draft.put(tiers.get(j),ids);
                }
                menu.put("library_draft",draft);
            }
            for (int i = 0; i < buttons.size(); i++) {
                if(name.equals("Bonfire")) {
                    if(at.equals("INTRO")) transitions.put(String.valueOf(i),object("phases",list("CHOOSE")));
                    else if(at.equals("CHOOSE")) {
                        boolean eligible=com.megacrit.cardcrawl.cards.CardGroup.getGroupWithoutBottledCards(
                                AbstractDungeon.player.masterDeck.getPurgeableCards()).size()>0;
                        if(eligible)transitions.put(String.valueOf(i),object("screens",list("GRID")));
                        else {
                            Map<String,Object> choice=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                            choice.put("kind","CONTINUE");choice.put("followup","EVENT");
                            choice.put("outcomes",list(object("probability",1.0,"effects",list())));
                            transitions.put(String.valueOf(i),object("phases",list("COMPLETE")));
                        }
                    } else if(at.equals("COMPLETE"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("KnowingSkull")) {
                    if(at.equals("INTRO_1"))transitions.put(String.valueOf(i),object("phases",list("ASK")));
                    else if(at.equals("ASK") && i==3) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("COMPLETE")));
                    } else if(at.equals("COMPLETE"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Duplicator")) {
                    if(at.equals("0") && i==0)transitions.put(String.valueOf(i),object("screens",list("GRID")));
                    else if(at.equals("0") && i==1) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("2")));
                    } else if(at.equals("2"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("DrugDealer")) {
                    if(at.equals("0") && i==1) transitions.put(String.valueOf(i),object("screens",list("GRID")));
                    else if(at.equals("0")) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("1")));
                    } else if(at.equals("1")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("WeMeetAgain"))transitions.put(String.valueOf(i),at.equals("INTRO")
                        ?object("phases",list("COMPLETE")):object("screens",list("MAP")));
                if(name.equals("BackToBasics")) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    if(at.equals("INTRO") && i==0) {
                        boolean canPurge=com.megacrit.cardcrawl.cards.CardGroup.getGroupWithoutBottledCards(
                                AbstractDungeon.player.masterDeck.getPurgeableCards()).size()>0;
                        if(canPurge)transitions.put("0",object("screens",list("GRID")));
                        else {
                            c.put("kind","CONTINUE");c.put("followup","EVENT");
                            c.put("outcomes",list(object("probability",1.0,"effects",list())));
                            transitions.put("0",object("phases",list("COMPLETE")));
                        }
                    } else if(at.equals("INTRO") && i==1) {
                        c.put("followup","EVENT");
                        c.put("outcomes",list(object("probability",1.0,"effects",list(starterUpgradeEffect()))));
                        transitions.put("1",object("phases",list("COMPLETE")));
                    } else if(at.equals("COMPLETE"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Vampires")) {
                    if(at.equals("0") && i==(flag(event,"hasVial")?2:1)) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("2")));
                    } else if(at.equals("1") || at.equals("2"))
                        transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("UpgradeShrine")) {
                    if(at.equals("INTRO") && i==1) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put("1",object("phases",list("COMPLETE")));
                    } else if(at.equals("INTRO") && i==0)transitions.put("0",object("screens",list("GRID")));
                    else if(at.equals("COMPLETE"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("GoldShrine") || name.equals("GoopPuddle")) {
                    if(at.equals("INTRO")) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list(name.equals("GoldShrine")?"COMPLETE":"RESULT")));
                    } else if(at.equals(name.equals("GoldShrine")?"COMPLETE":"RESULT"))
                        transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Sssserpent")) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    if(at.equals("INTRO")) {
                        c.put("kind",i==0?"COMMIT":"LEAVE");c.put("followup","EVENT");
                        c.put("outcomes",list(object("probability",1.0,"effects",list())));
                        transitions.put(String.valueOf(i),object("phases",list(i==0?"AGREE":"DISAGREE")));
                    } else if(at.equals("AGREE") && i==0) {
                        c.put("kind","EFFECT");c.put("followup","EVENT");
                        c.put("outcomes",list(object("probability",1.0,"effects",list(
                                effect("gain_gold","amount",number(event,"goldReward")),
                                effect("add_card","card_id",((AbstractCard)field(event,"curse")).cardID,"amount",1)))));
                        transitions.put("0",object("phases",list("COMPLETE")));
                    } else if(at.equals("DISAGREE") || at.equals("COMPLETE"))
                        transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Beggar")) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    if(at.equals("INTRO")) {
                        c.put("kind",i==0?"EFFECT":"LEAVE");c.put("followup","EVENT");
                        c.put("outcomes",list(object("probability",1.0,"effects",i==0
                                ?list(effect("lose_gold","amount",number(event,"GOLD_COST"))):list())));
                        transitions.put(String.valueOf(i),object("phases",list(i==0?"GAVE_MONEY":"LEAVE")));
                    } else if(at.equals("GAVE_MONEY") && i==0)
                        transitions.put("0",object("screens",list("GRID")));
                    else if(at.equals("LEAVE"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Nest")) {
                    if(at.equals("0") || at.equals("1")) {
                        transitions.put(String.valueOf(i),object("phases",list(at.equals("0")?"1":"2")));
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                    } else if(at.equals("2")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if((name.equals("FaceTrader") || name.equals("Designer")) && at.equals("INTRO"))
                    transitions.put(String.valueOf(i),object("phases",list("MAIN")));
                if(name.equals("FaceTrader") && at.equals("MAIN") && i==2
                        || name.equals("ShiningLight") && at.equals("INTRO") && i==1
                        || name.equals("PurificationShrine") && at.equals("INTRO") && i==1
                        || name.equals("AccursedBlacksmith") && at.equals("0") && i==2) {
                    ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                    transitions.put(String.valueOf(i),object("phases",list(name.equals("FaceTrader")?"RESULT":name.equals("AccursedBlacksmith")?"2":"COMPLETE")));
                }
                if(name.equals("Mushrooms") && at.equals("0") && i==0) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    c.put("followup","EVENT");
                    c.put("outcomes",list(object("probability",1.0,"effects",list(effect("commit_encounter","encounter","The Mushroom Lair")))));
                    transitions.put("0",object("phases",list("2")));
                } else if(name.equals("Mushrooms") && at.equals("0") && i==1) {
                    ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                    transitions.put("1",object("phases",list("1")));
                } else if(name.equals("Mushrooms") && at.equals("2") && i==0) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    c.put("kind","COMBAT");c.put("followup","COMBAT");
                    c.put("outcomes",list(object("probability",1.0,"effects",list(effect("start_combat","encounter","The Mushroom Lair")))));
                } else if(name.equals("Mushrooms") && at.equals("1")) {
                    transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("WomanInBlue") && at.equals("INTRO") && i==3) {
                    ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                    transitions.put(String.valueOf(i),object("phases",list("RESULT")));
                }
                if (name.equals("BigFish") && (at.equals("INTRO") || at.equals("0")))
                    transitions.put(String.valueOf(i), object("phases", list(at.equals("INTRO") ? "RESULT" : "1")));
                if ((name.equals("BigFish") || name.equals("LivingWall")) && at.equals("RESULT") || name.equals("Transmogrifier") && at.equals("COMPLETE"))
                    transitions.put(String.valueOf(i), object("screens", list("MAP")));
                if(name.equals("GoldenWing")) {
                    Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                    if(at.equals("INTRO") && i==0) {
                        // Paying HP opens a second dialog, not the grid until its next button.
                        c.put("followup","EVENT");
                        c.put("outcomes",list(object("probability",1.0,"effects",list(effect("lose_hp","amount",number(event,"damage"))))));
                        transitions.put(String.valueOf(i),object("phases",list("PURGE")));
                    } else if(at.equals("INTRO") && i==1) {
                        c.put("followup","EVENT");
                        c.put("outcomes",goldenWingGoldOutcomes(number(event,"MIN_GOLD"),number(event,"MAX_GOLD")));
                        view.put("decision_kind","STOCHASTIC");
                        transitions.put(String.valueOf(i),object("phases",list("MAP")));
                    } else if(at.equals("INTRO") && i==2) {
                        c.put("followup","EVENT"); transitions.put(String.valueOf(i),object("phases",list("MAP")));
                    } else if(at.equals("PURGE")) {
                        transitions.put(String.valueOf(i),object("screens",list("GRID")));
                    } else if(at.equals("MAP")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Cleric")) {
                    if(at.equals("0") && (i==0 || i==2)) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("99")));
                    } else if(at.equals("0") && i==1) transitions.put(String.valueOf(i),object("screens",list("GRID")));
                    else if(at.equals("99")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("ForgottenAltar") && at.equals("99")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                if(name.equals("TheLibrary")) {
                    if(at.equals("1"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                    else if(at.equals("0"))transitions.put(String.valueOf(i),i==0?object("screens",list("GRID")):object("phases",list("1")));
                }
                if(name.equals("ScrapOoze")) {
                    if(at.equals("0") && i==1) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("1")));
                    } else if(at.equals("1")) transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("DeadAdventurer")) {
                    if(at.equals("INTRO") && i==1) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list("ESCAPE")));
                    } else if(at.equals("INTRO") && i==0) {
                        Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                        for(Object outcome:(List<?>)c.get("outcomes"))for(Object value:(List<?>)((Map<?,?>)outcome).get("effects")) {
                            Map<String,Object> effect=(Map<String,Object>)value;
                            if(effect.get("type").equals("start_combat"))effect.put("type","commit_encounter");
                        }
                        transitions.put(String.valueOf(i),object("phases",list("INTRO","FAIL","SUCCESS")));
                    } else if(at.equals("FAIL") && i==0) {
                        Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                        c.put("kind","COMBAT");c.put("followup","COMBAT");
                        String encounter=number(event,"enemy")==0?"3 Sentries":number(event,"enemy")==1?"Gremlin Nob":"Lagavulin Event";
                        c.put("outcomes",list(object("probability",1.0,"effects",list(effect("start_combat","encounter",encounter)))));
                    } else if(at.equals("ESCAPE") || at.equals("SUCCESS"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if(name.equals("Ghosts")) {
                    if(at.equals("0")) {
                        ((Map<String,Object>)((List<?>)view.get("choices")).get(i)).put("followup","EVENT");
                        transitions.put(String.valueOf(i),object("phases",list(i==0?"1":"2")));
                    } else if(at.equals("1") || at.equals("2"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                }
                if (name.equals("GoldenIdolEvent")) {
                    if (at.equals("0") || at.equals("1")) {
                        transitions.put(String.valueOf(i), object("phases", list(at.equals("0") && i == 0 ? "1" : "2")));
                        ((Map<String,Object>) ((List<?>) view.get("choices")).get(i)).put("followup", "EVENT");
                    } else if (at.equals("2")) transitions.put(String.valueOf(i), object("screens", list("MAP")));
                }
                if (name.equals("GremlinMatchGame") && (at.equals("INTRO") || at.equals("RULE_EXPLANATION")))
                    transitions.put(String.valueOf(i), object("phases", list(at.equals("INTRO") ? "RULE_EXPLANATION" : "PLAY")));
                if (name.equals("NeowEvent") && (at.equals("0") || at.equals("1") || at.equals("2") && i == 0))
                    transitions.put(String.valueOf(i), object("phases", list("3")));
                if (name.equals("CursedTome")) {
                    String next = at.equals("INTRO") ? (i == 0 ? "PAGE_1" : "END") : at.equals("PAGE_1") ? "PAGE_2" : at.equals("PAGE_2") ? "PAGE_3" : at.equals("PAGE_3") ? "LAST_PAGE" : "END";
                    transitions.put(String.valueOf(i), object("phases", list(next)));
                    if(at.equals("END"))transitions.put(String.valueOf(i),object("screens",list("MAP")));
                    if(at.equals("LAST_PAGE") && i==0) {
                        Map<String,Object> c=(Map<String,Object>)((List<?>)view.get("choices")).get(i);
                        c.put("followup","COMBAT_REWARD");c.put("outcomes",list(object("probability",1.0,"effects",list(effect("lose_hp","amount",number(event,"finalDmg")),effect("relic_reward","pool","BOOK")))));
                        transitions.put(String.valueOf(i),object("screens",list("COMBAT_REWARD")));
                    }
                }
            }
            if(name.equals("GremlinWheelGame")) {
                String landing=at.equals("INTRO")?"SPIN":at.equals("SPIN")?"COMPLETE":"LEAVE";
                transitions.put("0",at.equals("LEAVE")?object("screens",list("MAP")):object("phases",list(landing)));
            }
            menu.put("event_transitions", transitions);
        } else if (phase.equals("SHOP")) {
            var shop = AbstractDungeon.shopScreen;
            List<Object> items = new ArrayList<>(); int index = 0;
            Object sale = field(shop, "saleTag");
            for (AbstractCard c : shop.coloredCards) items.add(shopCard(c, "card:" + c.uuid, index++, sale != null && field(sale, "card") == c));
            for (AbstractCard c : shop.colorlessCards) items.add(shopCard(c, "card:" + c.uuid, index++, false));
            for (int i = 0; i < shop.getRelicsForHeadless().size(); i++) {
                var r = shop.getRelicsForHeadless().get(i);
                items.add(object("slot_id", "relic:" + i, "kind", "relic", "id", r.relic.relicId,
                        "native_index", i, "price", r.price, "upgrades", 0, "sold_out", r.isPurchased, "on_sale", false));
            }
            for (int i = 0; i < shop.getPotionsForHeadless().size(); i++) {
                var p = shop.getPotionsForHeadless().get(i);
                items.add(object("slot_id", "potion:" + i, "kind", "potion", "id", p.potion.ID,
                        "native_index", i, "price", p.price, "upgrades", 0, "sold_out", p.isPurchased, "on_sale", false));
            }
            menu.put("shop", object("items", items, "purge_cost", com.megacrit.cardcrawl.shop.ShopScreen.actualPurgeCost,
                    "purge_available", shop.purgeAvailable, "purge_cards", HeadlessStateExport.cards(shop.getPurgeableCardsForHeadless().group)));
        } else if (phase.equals("REWARD")) {
            menu.put("rewards", rewardRows());
            boolean returnShop=HeadlessFlowKernel.canReturnToShop();
            menu.put("return_shop",returnShop);
            boolean tinyHouse = HeadlessFlowKernel.canLeaveTinyHouseReward();
            if (tinyHouse) menu.put("boss_bonus_owner","Tiny House");
            boolean neowBonus = AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.neow.NeowRoom
                    && AbstractDungeon.getCurrRoom().event instanceof com.megacrit.cardcrawl.neow.NeowEvent
                    && AbstractDungeon.actNum==1 && AbstractDungeon.floorNum==0 && HeadlessFlowKernel.cancelVisible()
                    && number(AbstractDungeon.getCurrRoom().event,"screenNum")==99
                    && "BOSS_RELIC".equals(com.megacrit.cardcrawl.core.CardCrawlGame.metricData.neowBonus)
                    && AbstractDungeon.player.hasRelic("Tiny House");
            if(neowBonus)menu.put("neow_bonus_owner","Tiny House");
            menu.put("can_proceed", !flag(AbstractDungeon.overlayMenu.proceedButton, "isHidden") || returnShop || tinyHouse || neowBonus);
        } else if (phase.equals("BOSS_RELIC")) {
            List<Object> relics = new ArrayList<>();
            for (int i = 0; i < AbstractDungeon.bossRelicScreen.relics.size(); i++)
                relics.add(object("native_index", i, "id", AbstractDungeon.bossRelicScreen.relics.get(i).relicId));
            menu.put("boss_relics", relics); menu.put("can_skip_boss", true);
        } else if (phase.equals("CARD_PICK")) {
            var screen = AbstractDungeon.cardRewardScreen;
            var action = AbstractDungeon.actionManager.currentAction;
            String source=action==null?"":action.getClass().getSimpleName();
            Map<String,Object> reward=object("skip", !flag(field(screen, "skipButton"), "isHidden"),
                    "bowl", screen.isSingingBowlAvailableHeadless(),"discovery",flag(screen,"discovery"),"source_action",source);
            if(source.equals("ForeignInfluenceAction")) { reward.put("copies",1);reward.put("free_for_turn",flag(action,"upgraded")); }
            if(source.equals("DiscoveryAction")) { reward.put("copies",action.amount);reward.put("free_for_turn",true); }
            if(source.equals("CodexAction")) { reward.put("copies",1);reward.put("free_for_turn",false);reward.put("destination","draw_pile"); }
            menu.put("card_reward",reward);
        } else if (phase.equals("CARD_SELECT")) {
            boolean grid = AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID;
            Map<String, Object> selection;
            if (grid) {
                var g = AbstractDungeon.gridSelectScreen;
                String inferred=relicSelectionPurpose();
                String purpose = inferred.equals("bonfire_offer") ? inferred : g.forUpgrade ? "upgrade" : g.forTransform ? "transform" : g.forPurge ? "purge" : g.isJustForConfirming ? "obtain" : inferred;
                selection = object("kind", "grid", "purpose", purpose, "count", number(g, "numCards"),
                        "locked", g.isHeadlessSelectionLocked(), "can_confirm", g.isHeadlessConfirmAvailable(),
                        "any_number", g.anyNumber || g.forClarity, "can_pick_zero", g.anyNumber,
                        "auto_confirm", !(g.forUpgrade || g.forTransform || g.forPurge && number(g, "numCards") == 1));
                if(purpose.equals("scry")) {
                    var replay=HeadlessCombatExport.scryReplayEvidence();
                    if(replay!=null)menu.put("scry_replay",replay);
                }
                AbstractEvent selectionEvent=AbstractDungeon.getCurrRoom().event;
                if(selectionEvent!=null && selectionEvent.getClass().getSimpleName().equals("Bonfire")) {
                    selection.put("source_event_class","Bonfire");
                    selection.put("source_event_phase",String.valueOf(field(selectionEvent,"screen")));
                } else if(selectionEvent!=null && selectionEvent.getClass().getSimpleName().equals("Duplicator")) {
                    selection.put("source_event_class","Duplicator");
                    selection.put("source_event_phase",String.valueOf(number(selectionEvent,"screenNum")));
                } else if(selectionEvent!=null && selectionEvent.getClass().getSimpleName().equals("TheLibrary")) {
                    selection.put("source_event_class","TheLibrary");
                    selection.put("source_event_phase",String.valueOf(number(selectionEvent,"screenNum")));
                }
                if(selectionEvent!=null && selectionEvent.getClass().getSimpleName().equals("Designer"))
                    selection.put("designer_option", String.valueOf(field(selectionEvent,"option")));
                AbstractCard hovered = (AbstractCard) field(g, "hoveredCard");
                if (g.confirmScreenUp && hovered != null) selection.put("preview_uuid", hovered.uuid.toString());
            } else {
                var h = AbstractDungeon.handCardSelectScreen;
                String action = AbstractDungeon.actionManager.currentAction == null ? "" : AbstractDungeon.actionManager.currentAction.getClass().getSimpleName();
                String purpose = flag(h, "forUpgrade") ? "upgrade" : flag(h, "forTransform") ? "transform" :
                        action.equals("WarcryAction") || action.equals("PutOnDeckAction") ? "topdeck" : action.equals("DualWieldAction") ? "duplicate_combat" :
                        action.equals("ForethoughtAction") && "Forethought".equals(HeadlessCombatExport.singleHandSource()) ? "forethought" :
                        action.equals("NightmareAction") && "Night Terror".equals(HeadlessCombatExport.singleHandSource()) ? "nightmare" :
                        action.contains("Exhaust") ? "exhaust" : action.equals("GamblingChipAction") ? "gamble" : action.contains("Discard") ? "discard" : action.contains("Retain") ? "retain" : "unknown";
                boolean any = flag(h, "anyNumber") || h.upTo;
                selection = object("kind", "hand", "purpose", purpose, "count", h.numCardsToSelect,
                        "locked", false, "can_confirm", any || h.canPickZero && h.selectedCards.size() == 0 || h.selectedCards.size() == h.numCardsToSelect,
                        "any_number", any, "can_pick_zero", h.canPickZero, "auto_confirm", !any && !flag(h, "forUpgrade"));
                if (purpose.equals("duplicate_combat")) selection.put("copies", number(AbstractDungeon.actionManager.currentAction, "dupeAmount"));
            }
            menu.put("selection", selection);
            if (AbstractDungeon.previousScreen == AbstractDungeon.CurrentScreen.COMBAT_REWARD) menu.put("background_rewards", rewardRows());
        } else if (phase.equals("PROCEED")) menu.put("can_proceed", !flag(AbstractDungeon.overlayMenu.proceedButton, "isHidden"));
        HeadlessFlowExport.enrich(out);
        return (Map<String, Object>) wireTree(out);
    }
}
