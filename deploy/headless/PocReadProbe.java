import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.headless.HeadlessObservation;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native observation plus BOOT diagnostics; never a run launcher. */
public final class PocReadProbe {
    private static final Gson JSON = new Gson();

    private static final class ReadOnlyKernel extends GameKernel {
        @Override
        public void newRun(long seed, AbstractPlayer.PlayerClass character, int ascension) {
            throw new IllegalStateException("PoC run creation is disabled");
        }
    }

    private static void assertNoRun() {
        if (AbstractDungeon.player != null || AbstractDungeon.currMapNode != null
                || AbstractDungeon.map != null || AbstractDungeon.floorNum != 0
                || AbstractDungeon.actNum != 0 || Settings.seed != null || Settings.seedSet) {
            throw new IllegalStateException("Unexpected initialized run");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("dataRoot saveRoot");
        GameKernel.boot(args[0], args[1]);
        ReadOnlyKernel kernel = new ReadOnlyKernel();
        assertNoRun();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while ((command = input.readLine()) != null) {
                if (command.equals("quit")) break;
                if (!command.equals("state")) {
                    System.out.println("POC_REJECTED");
                    System.out.flush();
                    continue;
                }
                assertNoRun();
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("schema", "headless-boot-diagnostics-v1");
                state.put("source", "poc-wrapper-with-native-observation");
                state.put("lifecycle", "BOOT");
                state.put("playerInitialized", AbstractDungeon.player != null);
                state.put("mapInitialized", AbstractDungeon.map != null);
                state.put("roomInitialized", AbstractDungeon.currMapNode != null);
                state.put("floor", AbstractDungeon.floorNum);
                state.put("act", AbstractDungeon.actNum);
                state.put("seedInitialized", Settings.seed != null);
                state.put("seedSet", Settings.seedSet);
                state.put("graphicsType", String.valueOf(Gdx.graphics.getType()));
                state.put("glPresent", Gdx.gl != null || Gdx.gl20 != null || Gdx.gl30 != null);
                state.put("nativeObservationAvailable", true);
                state.put("nativeObservation", HeadlessObservation.build(kernel));
                System.out.println("POC_STATE " + JSON.toJson(state));
                System.out.flush();
            }
        }
        assertNoRun();
    }
}
