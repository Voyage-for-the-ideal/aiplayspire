import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded, explicitly permitted real-run smoke. No agent or collection writer. */
public final class RunSmokeTest {
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[2]).toRealPath();
        if (!root.toString().startsWith("/home/ubuntu/headless-spire-poc/runtime-acceptance-") ||
                !Files.isRegularFile(root.resolve("ALLOW_RUN_SMOKE")) || Files.exists(root.resolve("ALLOW_COLLECTION")))
            throw new IllegalStateException("Expected isolated smoke permit with collection closed");
        Path reports=root.resolve("reports");Files.createDirectories(reports);
        GameKernel.boot(args[0],args[1]);
        List<Object> results=new ArrayList<>();
        for (AbstractPlayer.PlayerClass role:AbstractPlayer.PlayerClass.values()) {
            if (!List.of("IRONCLAD","THE_SILENT","DEFECT","WATCHER").contains(role.name()))continue;
            GameKernel kernel=new HeadlessFlowKernel();
            kernel.showNeowForNextRun();kernel.newRun(4040001L+results.size(),role,0);
            List<Object> steps=new ArrayList<>();boolean combat=false;
            for (int step=0;step<12;step++) {
                GameKernel.Phase phase=kernel.phase();
                if (phase==null)phase=kernel.drainUntilDecision();
                steps.add(HeadlessInteractionExport.object("phase",phase.name(),"floor",AbstractDungeon.floorNum));
                if (phase==GameKernel.Phase.PLAYER_TURN) {
                    Files.writeString(reports.resolve("first-combat-"+role.name()+".json"),HeadlessCombatExport.toJson(kernel));
                    combat=true;break;
                }
                switch (phase) {
                    case NEOW: kernel.chooseDialogOption(0);break;
                    case CARD_PICK: kernel.chooseRewardCard(0);break;
                    case REWARD:
                        if(AbstractDungeon.combatRewardScreen.rewards.isEmpty())kernel.proceed();
                        else if(AbstractDungeon.combatRewardScreen.rewards.get(0).type==com.megacrit.cardcrawl.rewards.RewardItem.RewardType.CARD)kernel.openCardReward(0);
                        else kernel.collectReward(0);
                        break;
                    case CARD_SELECT:
                        if (AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.GRID)throw new IllegalStateException("Unexpected smoke hand select");
                        if(AbstractDungeon.gridSelectScreen.isHeadlessConfirmAvailable())kernel.confirmGrid();else kernel.chooseGridCard(0);
                        break;
                    case MAP:
                        Map<String,Object> observation=HeadlessInteractionExport.capture(kernel);
                        var menu=(Map<String,Object>)observation.get("interaction");
                        var choices=(List<Map<String,Object>>)menu.get("map_choices");
                        if(choices.isEmpty())throw new IllegalStateException("No first map choice");
                        var target=choices.get(0);kernel.chooseMapNode(((Number)target.get("x")).intValue(),((Number)target.get("y")).intValue());
                        break;
                    default: throw new IllegalStateException("Unexpected smoke phase "+phase);
                }
            }
            if(!combat)throw new IllegalStateException("Smoke action limit reached for "+role);
            results.add(HeadlessInteractionExport.object("role",role.name(),"first_combat_observed",true,"steps",steps));
            Files.writeString(reports.resolve("run-smoke.json"),JSON.toJson(HeadlessInteractionExport.object("schema","headless-real-run-smoke-v1",
                    "scope","real-first-combat-only","collection_started",false,"agent_started",false,"roles",results)));
            System.out.println("RUN_SMOKE_FIRST_COMBAT role="+role+" steps="+steps.size()+" collection=false");
        }
        if(results.size()!=4)throw new AssertionError("Expected four real roles");
    }
}
