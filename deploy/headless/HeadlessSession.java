import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.headless.GameKernel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Serial gated JSONL driver for staged real-run acceptance, never a network server. */
public final class HeadlessSession {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static long sequence=0,lastId=0;
    private static String lastRequest=null,lastReply=null;
    private static boolean poisoned=false;
    private static Map<String,Object> lastExecuted=null;
    private static String hash(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private static int integer(JsonArray a,int i) { return a.get(i).getAsBigDecimal().intValueExact(); }
    private static Map<String,Object> state(GameKernel kernel) {
        var observation=HeadlessCombatExport.capture(kernel);
        if(lastExecuted!=null)observation.put("driver_receipt",lastExecuted);
        return observation;
    }
    public static void main(String[] args) throws Exception {
        PrintStream protocol=System.out;System.setOut(System.err);
        Path root=Path.of(args[2]).toRealPath();
        if(!root.toString().startsWith("/home/ubuntu/headless-spire-poc/runtime-acceptance-") || !Files.isRegularFile(root.resolve("ALLOW_RUN_SMOKE")))
            throw new IllegalStateException("Expected isolated real-run permit");
        GameKernel.boot(args[0],args[1]);HeadlessFlowKernel kernel=new HeadlessFlowKernel();
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        String line;
        while((line=input.readLine())!=null) {
            JsonObject request;
            try { request=JsonParser.parseString(line).getAsJsonObject(); }
            catch(Exception e) { protocol.println(JSON.toJson(HeadlessInteractionExport.object("supported",false,"reason","malformed_request")));continue; }
            long id=request.get("request_id").getAsBigDecimal().longValueExact();
            String encoded=JSON.toJson(request);
            if(id==lastId && encoded.equals(lastRequest)) { protocol.println(lastReply);protocol.flush();continue; }
            JsonObject reply=new JsonObject();reply.addProperty("request_id",id);
            try {
                if(id<=lastId)throw new IllegalStateException("stale_or_conflicting_request_id");
                if(poisoned)throw new IllegalStateException("session_poisoned");
                if(!Files.isRegularFile(root.resolve("ALLOW_RUN_SMOKE")))throw new IllegalStateException("run_permit_removed");
                String operation=request.get("operation").getAsString();
                if(!operation.equals("state")) {
                    String before=JSON.toJson(state(kernel));
                    if(!hash(before).equals(request.get("expected_state_token").getAsString()))throw new IllegalStateException("stale_state_token");
                }
                boolean actionStarted=false;
                try {
                    if(operation.equals("new_run")) {
                        var prior=kernel.phase();
                        if(prior!=GameKernel.Phase.BOOT && prior!=GameKernel.Phase.GAME_OVER && prior!=GameKernel.Phase.RUN_COMPLETE)
                            throw new IllegalStateException("previous_run_not_terminal");
                        if(prior!=GameKernel.Phase.BOOT) {
                            var settled=JSON.toJsonTree(state(kernel)).getAsJsonObject();
                            if(settled.getAsJsonObject("interaction").getAsJsonObject("flow").get("terminal").isJsonNull())
                                throw new IllegalStateException("previous_run_has_no_terminal_evidence");
                        }
                        actionStarted=true;kernel.showNeowForNextRun();
                        kernel.newRun(request.get("seed").getAsBigDecimal().longValueExact(),AbstractPlayer.PlayerClass.valueOf(request.get("character").getAsString()),request.get("ascension").getAsBigDecimal().intValueExact());
                        if(kernel.phase()==null)kernel.drainUntilDecision();
                        sequence++;
                        lastExecuted=HeadlessInteractionExport.object("request_id",id,"sequence",sequence,"method","newRun","args",java.util.List.of(),"card_uuid",null,"source_state_token",request.get("expected_state_token").getAsString());
                    } else if(operation.equals("action")) {
                        String method=request.get("method").getAsString();JsonArray a=request.getAsJsonArray("args");
                        var dungeonPlayer=com.megacrit.cardcrawl.dungeons.AbstractDungeon.player;
                        var grid=com.megacrit.cardcrawl.dungeons.AbstractDungeon.gridSelectScreen;
                        String cardUuid=method.equals("playCard")?dungeonPlayer.hand.group.get(integer(a,0)).uuid.toString():null;
                        if(method.equals("chooseGridCard") && kernel.phase()==GameKernel.Phase.CARD_SELECT
                                && com.megacrit.cardcrawl.dungeons.AbstractDungeon.screen==com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen.GRID)
                            cardUuid=grid.targetGroup.group.get(integer(a,0)).uuid.toString();
                        actionStarted=true;
                        switch(method) {
                            case "playCard":kernel.playCard(integer(a,0),integer(a,1));break;
                            case "usePotion":kernel.usePotion(integer(a,0),integer(a,1));break;
                            case "discardPotion":kernel.discardPotion(integer(a,0));break;
                            case "endTurn":kernel.endTurn();break;
                            case "chooseDialogOption":kernel.chooseDialogOption(integer(a,0));break;
                            case "chooseMatchCard":kernel.chooseMatchCard(integer(a,0));break;
                            case "chooseMapNode":kernel.chooseMapNode(integer(a,0),integer(a,1));break;
                            case "collectReward":kernel.collectReward(integer(a,0));break;
                            case "openCardReward":kernel.openCardReward(integer(a,0));break;
                            case "chooseRewardCard":kernel.chooseRewardCard(integer(a,0));break;
                            case "skipRewardCard":kernel.skipRewardCard();break;
                            case "chooseSingingBowl":kernel.chooseSingingBowl();break;
                            case "chooseGridCard":kernel.chooseGridCard(integer(a,0));break;
                            case "confirmGrid":kernel.confirmGrid();break;
                            case "chooseHandCard":kernel.chooseHandCard(integer(a,0));break;
                            case "confirmHandSelect":kernel.confirmHandSelect();break;
                            case "useCampfireOption":kernel.useCampfireOption(integer(a,0));break;
                            case "openChest":kernel.openChest();break;
                            case "chooseBossRelic":kernel.chooseBossRelic(integer(a,0));break;
                            case "skipBossRelic":kernel.skipBossRelic();break;
                            case "enterShop":kernel.enterShop();break;
                            case "buyCard":kernel.buyCard(integer(a,0));break;
                            case "buyRelic":kernel.buyRelic(integer(a,0));break;
                            case "buyPotion":kernel.buyPotion(integer(a,0));break;
                            case "purgeCard":kernel.purgeCard(integer(a,0));break;
                            case "leaveShop":kernel.leaveShop();break;
                            case "proceed":kernel.proceed();break;
                            default:actionStarted=false;throw new IllegalArgumentException("unknown_native_method");
                        }
                        sequence++;
                        lastExecuted=HeadlessInteractionExport.object("request_id",id,"sequence",sequence,"method",method,"args",a,"card_uuid",cardUuid,"source_state_token",request.get("expected_state_token").getAsString());
                    } else if(!operation.equals("state"))throw new IllegalArgumentException("unknown_operation");
                } catch(Throwable error) { if(actionStarted)poisoned=true;throw error; }
                Map<String,Object> observation=state(kernel);
                reply.addProperty("supported",true);reply.addProperty("sequence",sequence);
                reply.addProperty("state_token",hash(JSON.toJson(observation)));reply.add("state",JSON.toJsonTree(observation));
            } catch(Throwable error) {
                reply.addProperty("supported",false);reply.addProperty("reason",error.getClass().getSimpleName()+":"+error.getMessage());reply.addProperty("poisoned",poisoned);
            }
            lastId=id;lastRequest=encoded;lastReply=JSON.toJson(reply);
            protocol.println(lastReply);protocol.flush();
        }
    }
}
