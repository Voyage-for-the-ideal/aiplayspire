import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.random.Random;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Local cards/monster/RNG objects; no player, dungeon, or native command. */
public final class CombatExportFixtureTest {
    static final class LocalCultist extends AbstractMonster {
        LocalCultist() { super("fixture", "Cultist",50,0,0,10,10,null); nextMove=3;intent=Intent.BUFF;moveHistory.add((byte)3); }
        @Override public void takeTurn() { throw new AssertionError("No game action in fixture"); }
        @Override protected void getMove(int n) { throw new AssertionError("No roll in fixture"); }
    }
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[2]);
        StateExportFixtureTest.main(new String[]{args[0],args[1],output.resolveSibling("base-state-fixture.json").toString()});
        Gson gson=new com.google.gson.GsonBuilder().serializeNulls().create();JsonObject base=JsonParser.parseString(Files.readString(output.resolveSibling("base-state-fixture.json"))).getAsJsonObject();
        var monster=new LocalCultist();
        AbstractCard headbutt=CardLibrary.getCard("Headbutt").makeCopy();
        var headbuttCleanup=HeadlessCombatExport.headbuttCleanupCard(headbutt,headbutt,0,false,false,false);
        if(!headbuttCleanup.get("targetIndex").equals(0)
                || !((Map<?,?>)headbuttCleanup.get("card")).get("card_id").equals("Headbutt"))
            throw new AssertionError("Headbutt source identity missing");
        try {
            HeadlessCombatExport.headbuttCleanupCard(headbutt,headbutt,0,true,false,false);
            throw new AssertionError("Unknown Headbutt exhaust accepted");
        } catch(IllegalStateException expected) {}
        try {
            HeadlessCombatExport.headbuttCleanupCard(headbutt,headbutt.makeCopy(),0,false,false,false);
            throw new AssertionError("Stale Headbutt in-flight identity accepted");
        } catch(IllegalStateException expected) {}
        try {
            HeadlessCombatExport.headbuttContinuation(List.of());
            throw new AssertionError("Missing Headbutt cleanup accepted");
        } catch(IllegalStateException expected) {}
        var puzzle=new com.megacrit.cardcrawl.relics.CentennialPuzzle();
        var pellets=new com.megacrit.cardcrawl.relics.OrangePellets();
        var flags=(Map<?,?>)HeadlessCombatExport.relic(pellets).get("_private");
        for(String kind:List.of("ATTACK","SKILL","POWER"))
            if(!(flags.get("OrangePellets."+kind) instanceof Boolean))throw new AssertionError("Missing Pellets source flag "+kind);
        var puzzleFacts=HeadlessCombatExport.relic(puzzle);
        if(!(((Map<?,?>)puzzleFacts.get("_private")).get("CentennialPuzzle.usedThisCombat") instanceof Boolean))
            throw new AssertionError("Static Centennial Puzzle latch missing");
        var waiting=List.<com.megacrit.cardcrawl.actions.AbstractGameAction>of(new com.megacrit.cardcrawl.actions.utility.WaitAction(0.25f));
        if(HeadlessCombatExport.gamblingContinuation(waiting).size()!=1)throw new AssertionError("Missing Gambler wait continuation");
        var refresh=HeadlessCombatExport.gamblingContinuation(List.of(new com.megacrit.cardcrawl.actions.utility.HandCheckAction(),
                new com.megacrit.cardcrawl.actions.utility.WaitAction(0.25f)));
        var refreshRow=(Map<?,?>)refresh.get(0);
        if(refresh.size()!=2 || !refreshRow.get("state_class").equals("savestate.actions.HandCheckActionState")
                || !((Map<?,?>)refreshRow.get("state")).get("owner_index").equals(-1)
                || !((Map<?,?>)refreshRow.get("state")).get("derived_refresh").equals(Boolean.TRUE))
            throw new AssertionError("Gambler hand refresh identity/owner lost");
        try {
            HeadlessCombatExport.gamblingContinuation(List.of(new com.megacrit.cardcrawl.actions.common.GainEnergyAction(1)));
            throw new AssertionError("Unexpected Gambler executable continuation accepted");
        } catch(IllegalStateException expected) {
            if(!expected.getMessage().contains("Unmapped Gambler"))throw expected;
        }
        var liquidRefresh=HeadlessCombatExport.liquidContinuation(List.of(new com.megacrit.cardcrawl.actions.utility.HandCheckAction()));
        if(liquidRefresh.size()!=1 || !liquidRefresh.get(0).equals(refreshRow))
            throw new AssertionError("Liquid Memories lost its known hand refresh");
        if(!HeadlessCombatExport.liquidContinuation(List.of()).isEmpty())throw new AssertionError("Liquid Memories queue invented an action");
        for(var queue:List.of(waiting,List.<com.megacrit.cardcrawl.actions.AbstractGameAction>of(
                    new com.megacrit.cardcrawl.actions.common.GainEnergyAction(1)),
                List.<com.megacrit.cardcrawl.actions.AbstractGameAction>of(new com.megacrit.cardcrawl.actions.utility.HandCheckAction(),
                    new com.megacrit.cardcrawl.actions.utility.HandCheckAction()))) {
            try {
                HeadlessCombatExport.liquidContinuation(queue);
                throw new AssertionError("Unknown Liquid Memories continuation accepted");
            } catch(IllegalStateException expected) {
                if(!expected.getMessage().contains("Unmapped"))throw expected;
            }
        }
        try {
            HeadlessCombatExport.gamblingContinuation(List.of(new com.megacrit.cardcrawl.actions.utility.WaitAction(0.25f),
                    new com.megacrit.cardcrawl.actions.utility.HandCheckAction()));
            throw new AssertionError("Reordered Gambler hand refresh accepted");
        } catch(IllegalStateException expected) {
            if(!expected.getMessage().contains("Unmapped Gambler"))throw expected;
        }
        try {
            HeadlessCombatExport.gamblingContinuation(List.of(new com.megacrit.cardcrawl.actions.utility.WaitAction(0.25f),
                    new com.megacrit.cardcrawl.actions.utility.WaitAction(0.25f)));
            throw new AssertionError("Duplicate Gambler wait continuation accepted");
        } catch(IllegalStateException expected) {
            if(!expected.getMessage().contains("Unmapped Gambler"))throw expected;
        }
        String[] names={"monster","map","event","merchant","card","treasure","relic","potion","monster_hp","ai","shuffle","card_random","misc","headless"};
        Map<String,Random> rngs=new TreeMap<>();
        for (int i=0;i<names.length;i++) {
            Random r=new Random(12345L+i);r.random=new RandomXS128(0xf123456789abcdefL+i,0x8123456789abcdefL-i);r.counter=11+i;rngs.put(names[i],r);
        }
        var payload=HeadlessCombatExport.rngPayload(rngs);Map<String,Object> golden=new TreeMap<>();
        for (String name:new String[]{"ai","card_random","misc","monster_hp","potion","shuffle"}) {
            Random copy=rngs.get(name).copy();List<Integer> samples=new ArrayList<>();for(int i=0;i<16;i++)samples.add(copy.random(999));golden.put(name,samples);
        }
        Map<String,Object> roles=new TreeMap<>();
        String[] classes={"IRONCLAD","THE_SILENT","DEFECT","WATCHER"},ids={"Strike_R","Strike_G","Strike_B","Strike_P"};
        for (int role=0;role<4;role++) {
            AbstractCard card=CardLibrary.getCard(ids[role]).makeCopy();card.uuid=UUID.fromString("00000000-0000-4000-8000-000000000001");
            var hand=HeadlessCombatExport.cards(List.of(card));var empty=new ArrayList<>();
            JsonObject raw=base.deepCopy();raw.addProperty("phase","PLAYER_TURN");raw.addProperty("act",1);raw.addProperty("floor",1);raw.add("map",null);
            var p=raw.getAsJsonObject("player");p.addProperty("className",classes[role]);p.addProperty("energy",3);p.addProperty("hp",70);p.addProperty("maxHp",80);p.addProperty("gold",99);
            p.add("masterDeck",gson.toJsonTree(HeadlessStateExport.cards(List.of(card))));p.add("hand",gson.toJsonTree(HeadlessStateExport.cards(List.of(card))));
            for (String pile:new String[]{"drawPile","discardPile","exhaustPile","relics","potions"})p.add(pile,gson.toJsonTree(empty));
            raw.add("monsters",gson.toJsonTree(List.of(HeadlessInteractionExport.object("id","Cultist","name","fixture","hp",50,"maxHp",50,"block",0,"intent","BUFF"))));
            var counters=HeadlessInteractionExport.object("seed",12345L);for(var r:rngs.entrySet())counters.put(r.getKey()+"_rng_counter",r.getValue().counter);
            var player=HeadlessInteractionExport.object("chosen_class_name",classes[role],"creature",HeadlessInteractionExport.object("id",classes[role],"current_health",70,"max_health",80,"current_block",0,"gold",99,"powers",empty),
                "game_hand_size",5,"energy_manager_energy",3,"energy_panel_total_energy",3,"max_orbs",0,"stance","Neutral",
                "is_ending_turn",false,"card_in_use",null,"limbo",empty,"master_deck",hand,"hand",hand,"draw_pile",empty,"discard_pile",empty,"exhaust_pile",empty,"relics",empty,"potions",empty,"orbs",empty,"orbs_channeled_this_combat",empty);
            var snapshot=HeadlessInteractionExport.object("_engine","headless","_headless_rng",payload,"floor_num",1,"act_num",1,"ascension_level",10,"turn",1,"screen_name","NONE","is_screen_up",false,"end_turn_queued",false,
                "total_discarded_this_turn",0,"cards_played_this_turn",empty,"cards_played_this_turn_backup",empty,"cards_played_this_combat",empty,"player_state",player,"rng_state",counters,"cur_map_node_state",HeadlessInteractionExport.object("phase_name","COMBAT","monster_data",List.of(HeadlessCombatExport.monster(monster))));
            raw.add("combat",gson.toJsonTree(HeadlessInteractionExport.object("schema",HeadlessCombatExport.SCHEMA,"snapshot",snapshot,
                "legal_actions",List.of(HeadlessInteractionExport.object("type","CARD","card_index",0,"monster_index",0,"uuid",card.uuid.toString()),HeadlessInteractionExport.object("type","END")))));
            roles.put(classes[role],raw);
        }
        var global=(RandomXS128)MathUtils.random;long a=global.getState(0),b=global.getState(1);String stable=gson.toJson(payload);
        for (int i=0;i<200;i++) {
            if(!stable.equals(gson.toJson(HeadlessCombatExport.rngPayload(rngs))))throw new AssertionError("RNG read changed");
            if(!"BOOT".equals(HeadlessCombatExport.capture(NoRunGuard.kernel()).get("phase")))throw new AssertionError("Run started");
            if(!gson.toJson(puzzleFacts).equals(gson.toJson(HeadlessCombatExport.relic(puzzle))))throw new AssertionError("Relic export changed static latch");
        }
        if(global.getState(0)!=a || global.getState(1)!=b)throw new AssertionError("Combat export consumed global RNG");
        NoRunGuard.verify();
        Files.writeString(output,gson.toJson(HeadlessInteractionExport.object("roles",roles,"rng_golden",golden,"puzzle_relic",puzzleFacts,"policy",HeadlessCombatExport.RNG_POLICY)));
        System.out.println("COMBAT_EXPORT_FIXTURE_OK reads=200 roles=4 local_rng_streams=14 native_actions=0 run_started=false rng_unchanged=true");
    }
}
