package aiplayspire.offline;

import battleaimod.battleai.CommandRunnerController;
import battleaimod.networking.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import ludicrousspeed.LudicrousSpeedMod;
import savestate.SaveState;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test-only scripted real-game replay. Points at live card objects, not indices picked by C++. */
final class SelectorSmoke {
    private final String mode;
    private NativeEngine engine;
    private AbstractCard source, a, b, untouched, oldExhume;
    private CommandRunnerController runner;
    private JsonObject before, forecast;
    private int step, initialTurn, initialHp, cardOrdinal;
    private boolean done;
    private List<AbstractCard> choices;
    private final Path trace = Paths.get(System.getProperty("battleai.trace"));

    SelectorSmoke(String mode) { this.mode = mode; }

    private AbstractCard card(String id) {
        AbstractCard template=CardLibrary.getCard(id);
        check(template!=null,"Missing card in live library: "+id);
        AbstractCard c=template.makeCopy();
        c.uuid=UUID.nameUUIDFromBytes((mode+":"+cardOrdinal++).getBytes(StandardCharsets.UTF_8));
        return c;
    }
    private static JsonObject snapshot() {
        return BattleStateProjection.normalize(new SaveState().jsonEncode()).getAsJsonObject();
    }
    private void emit(String kind, JsonObject detail) throws Exception {
        detail.addProperty("kind", kind); detail.addProperty("case", mode);
        Files.write(trace, (detail.toString()+"\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void check(boolean ok, String reason) {
        if (!ok) throw new IllegalStateException(reason);
    }
    private void setup() throws Exception {
        AbstractPlayer p=AbstractDungeon.player;
        p.hand.clear();p.drawPile.clear();p.discardPile.clear();p.exhaustPile.clear();
        for (int i=0;i<7;i++) p.drawPile.addToTop(card("Strike_R"));
        final boolean meditate=mode.startsWith("meditate"), forethought=mode.startsWith("forethought");
        source=card(meditate?"Meditate":forethought?"Forethought":mode.startsWith("exhume")?"Exhume":"Omniscience");
        if (mode.endsWith("-plus") || mode.endsWith("-zero")) source.upgrade();
        p.hand.addToTop(source);
        a=card("Defend_R"); b=card("Defend_R"); untouched=card("Bash");
        if (meditate) {
            p.hand.addToTop(card("Strike_R"));
            p.discardPile.group.addAll(Arrays.asList(a,b,untouched));
            choices=mode.endsWith("-plus")?Arrays.asList(b,a):Collections.singletonList(b);
        } else if (forethought) {
            p.hand.group.addAll(Arrays.asList(a,b,untouched));
            choices=mode.endsWith("-zero")?Collections.emptyList():mode.endsWith("-plus")?Arrays.asList(b,a):Collections.singletonList(b);
        } else if (mode.startsWith("exhume")) {
            oldExhume=card("Exhume");
            if(!mode.endsWith("-single")) p.exhaustPile.addToTop(a);
            p.exhaustPile.group.addAll(Arrays.asList(oldExhume,b));
            choices=Collections.singletonList(b);
        } else {
            if (mode.endsWith("-attack")) a=card("Strike_R");
            if (mode.endsWith("-scry")) a=card("CutThroughFate");
            p.drawPile.group.addAll(Arrays.asList(a,b));
            choices=Collections.singletonList(a);
        }
        EnergyPanel.setEnergy(6);
        p.hand.refreshHandLayout();p.hand.applyPowers();
        initialTurn=GameActionManager.turn;
        initialHp=AbstractDungeon.getMonsters().monsters.get(0).currentHealth;
        engine=new NativeEngine();
        Files.write(trace.resolveSibling("selector-start.json"),snapshot().toString().getBytes(StandardCharsets.UTF_8));
    }
    void tick() throws Exception {
        if(done) return;
        try {
            if (runner!=null && !runner.isDone()) return;
            if (LudicrousSpeedMod.mustRestart) throw new IllegalStateException("guard rejected live command");
            if (!BattleClientController.readyForUpdate()) return;
            if (engine==null) setup();
            if (runner!=null) {
                verify();
                runner=null;
                LudicrousSpeedMod.controller=null;
                step++;
            }
            int actionCount=1+choices.size()+(mode.startsWith("forethought")?1:mode.endsWith("-scry")?2:0);
            if(step==actionCount) {
                settle();
                emit("selector_verified",new JsonObject());done=true;engine.close();return;
            }
            JsonObject command=new JsonObject();
            if(step==0) {
                command.addProperty("type","CARD");command.addProperty("card_index",0);command.addProperty("monster_index",-1);
            } else if (step>choices.size()) {
                if(mode.endsWith("-scry")) {
                    check(AbstractDungeon.isScreenUp && AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID
                          && AbstractDungeon.gridSelectScreen.selectedCards.isEmpty(),"expected nested Scry before empty confirm");
                    command.addProperty("type","GRID_SELECT_CONFIRM");
                } else command.addProperty("type","HAND_SELECT_CONFIRM");
            }
            else {
                boolean hand=mode.startsWith("forethought");
                List<AbstractCard> options=hand?AbstractDungeon.player.hand.group:AbstractDungeon.gridSelectScreen.targetGroup.group;
                if(mode.startsWith("exhume")) check(!options.contains(oldExhume),"Exhume itself appeared in choices");
                int index=options.indexOf(choices.get(step-1));
                check(index>=0,"scripted card missing from live options");
                command.addProperty("type",hand?"HAND_SELECT":"GRID_SELECT");command.addProperty("card_index",index);
            }
            JsonObject req=new JsonObject();before=snapshot();
            req.add("snapshot",before);req.add("command",command);req.addProperty("operation","predict");
            forecast=null;
            forecast=engine.search(req);
            check(forecast.get("supported").getAsBoolean() && forecast.has("expected_after"),"no verifiable forecast");
            JsonObject envelope=forecast.getAsJsonArray("commands").get(0).getAsJsonObject();
            JsonObject traceRecord=new JsonObject();traceRecord.add("command",command);
            if(step>0 && step<=choices.size()) traceRecord.addProperty("chosen_uuid",choices.get(step-1).uuid.toString());
            emit("selector_command",traceRecord);
            runner=new CommandRunnerController(Collections.singletonList(CommandCodec.decode(envelope)),true);
            LudicrousSpeedMod.controller=runner;
        } catch(Exception | LinkageError error) {
            error.printStackTrace();
            done=true;
            if(engine!=null) engine.close();
            if(runner!=null) runner.cancel();
            LudicrousSpeedMod.controller=null;
            JsonObject failure=new JsonObject();failure.addProperty("error",error.toString());failure.addProperty("step",step);
            failure.add("before",before);failure.add("forecast",forecast);
            try {failure.add("actual", snapshot());} catch(RuntimeException ignored) {}
            Files.write(trace.resolveSibling("selector-error.json"),failure.toString().getBytes(StandardCharsets.UTF_8));
            JsonObject msg=new JsonObject();msg.addProperty("error",error.toString());emit("error",msg);
        }
    }
    private void verify() throws Exception {
        JsonObject actual=snapshot();
        String screen=AbstractDungeon.isScreenUp?AbstractDungeon.screen.name():"NONE";
        check(forecast.get("expected_screen").getAsString().equals(screen),"forecast screen mismatch");
        check(forecast.get("expected_after").equals(BattleStateProjection.fromSnapshot(actual)),"forecast projection mismatch");
        if (step>0 && screen.equals("GRID") && mode.startsWith("meditate"))
            check(AbstractDungeon.gridSelectScreen.selectedCards.equals(choices.subList(0,step)),"wrong interim grid choices");
        if (step>0 && screen.equals("HAND_SELECT"))
            check(AbstractDungeon.handCardSelectScreen.selectedCards.group.equals(choices.subList(0,step)),"wrong interim hand choices");
        JsonObject result=new JsonObject();result.addProperty("step",step);result.addProperty("screen",screen);
        JsonObject outcome=BattleStateProjection.fromSnapshot(actual);
        for(String field:new String[]{"turn","hp","energy","block","monsters","rng"}) result.add(field,outcome.get(field));
        result.addProperty("stance",AbstractDungeon.player.stance.ID);
        JsonArray selected=new JsonArray();
        if(screen.equals("GRID")) for(AbstractCard c:AbstractDungeon.gridSelectScreen.selectedCards) selected.add(c.uuid.toString());
        if(screen.equals("HAND_SELECT")) for(AbstractCard c:AbstractDungeon.handCardSelectScreen.selectedCards.group) selected.add(c.uuid.toString());
        result.add("selected",selected);
        for (String pile:new String[]{"hand","draw_pile","discard_pile","exhaust_pile"}) {
            JsonArray ids=new JsonArray();
            for(JsonElement c:actual.getAsJsonObject("player_state").getAsJsonArray(pile))
                ids.add(c.getAsJsonObject().get("uuid"));
            result.add(pile,ids);
        }
        emit("selector_applied",result);
    }
    private void settle() {
        AbstractPlayer p=AbstractDungeon.player;
        if(mode.startsWith("meditate")) {
            check(GameActionManager.turn==initialTurn+1 && "Calm".equals(p.stance.ID),"Meditate did not end the turn in Calm");
            check(p.hand.group.subList(0,choices.size()).equals(choices),"Meditate did not retain exact chosen cards in order");
            check(!p.hand.group.contains(untouched) && p.discardPile.group.contains(untouched),"Meditate moved an unchosen card");
        } else if(mode.startsWith("exhume")) {
            check(p.hand.group.contains(b) && !p.exhaustPile.group.contains(b),"Exhume did not retrieve the chosen card");
            List<AbstractCard> exhaust=mode.endsWith("-single")?Arrays.asList(oldExhume,source):Arrays.asList(a,oldExhume,source);
            check(p.exhaustPile.group.equals(exhaust),"Exhume lost/reordered an excluded copy or did not exhaust");
        } else if(mode.startsWith("forethought")) {
            List<AbstractCard> bottom=new ArrayList<>(choices);Collections.reverse(bottom);
            check(p.drawPile.group.subList(0,bottom.size()).equals(bottom),"Forethought did not put chosen cards at the bottom");
            check(p.hand.group.contains(untouched),"Forethought moved unchosen card");
            for(AbstractCard c:choices) check(c.freeToPlayOnce,"Forethought did not set free-to-play-once");
        } else {
            check(p.exhaustPile.group.equals(Arrays.asList(source,a)),"Omniscience did not exhaust exactly the original cards");
            long count=AbstractDungeon.actionManager.cardsPlayedThisCombat.stream().filter(c->c.cardID.equals(a.cardID)).count();
            check(count==2 && !p.drawPile.group.contains(a),"Omniscience did not play exactly two copies");
            if(mode.endsWith("-scry")) {
                check(p.hand.group.size()==2 && p.hand.group.get(0)==b,"Omniscience Scry did not draw twice in order");
                check(initialHp-AbstractDungeon.getMonsters().monsters.get(0).currentHealth==14,"Omniscience Scry damage mismatch");
            } else check(p.drawPile.group.contains(b),"Omniscience moved an unchosen card");
            if(mode.endsWith("-attack")) check(initialHp-AbstractDungeon.getMonsters().monsters.get(0).currentHealth==12,"Omniscience attack damage mismatch");
            else if(!mode.endsWith("-scry")) check(p.currentBlock==10,"Omniscience block mismatch");
            check(EnergyPanel.getCurrentEnergy()==6-source.cost,"Omniscience charged energy for replay");
        }
    }
}
