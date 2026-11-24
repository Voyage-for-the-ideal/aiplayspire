import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.headless.HeadlessObservation;
import java.util.Map;

/** Zero-run tests only; deliberately does not call upstream gameplay suites. */
public final class BootStateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        GameKernel.boot(args[0], args[1]);
        GameKernel kernel = NoRunGuard.kernel();
        NoRunGuard.verify();
        check(kernel.phase() == GameKernel.Phase.BOOT, "BOOT phase");
        RandomXS128 global = (RandomXS128) MathUtils.random;
        long before0 = global.getState(0), before1 = global.getState(1);
        String first = HeadlessObservation.toJson(kernel);
        String firstWire = ReadonlyStateServer.compatibleJson(kernel);
        check(firstWire.contains("\"choice_list\":[]"), "wire choice list");
        for (int i = 0; i < 200; i++) {
            Map<String, Object> observation = HeadlessObservation.build(kernel);
            check("BOOT".equals(observation.get("phase")), "native BOOT");
            check(observation.get("room") == null && observation.get("map") == null, "empty room/map");
            check(!observation.containsKey("player"), "no player");
            check(first.equals(HeadlessObservation.toJson(kernel)), "native state stability");
            Map<String, Object> state = ReadonlyStateServer.compatibleState(kernel);
            check(firstWire.equals(ReadonlyStateServer.compatibleJson(kernel)), "wire state stability");
            check("MAIN_MENU".equals(state.get("screen_type")), "legacy lifecycle");
            check(!state.containsKey("seed") && !state.containsKey("player")
                    && !state.containsKey("floor") && !state.containsKey("ascension_level"), "no invented run fields");
            check(Boolean.FALSE.equals(state.get("can_proceed")), "read-only choices");
        }
        Map<String, Object> settling = HeadlessObservation.build(new GameKernel() {
            @Override public Phase phase() { return null; }
        });
        check(settling.containsKey("phase") && settling.get("phase") == null, "null settling phase");
        check(global.getState(0) == before0 && global.getState(1) == before1, "read advances global RNG");
        check(MathUtils.random == global, "read replaces global RNG");
        check(ReadonlyStateServer.statusFor("/state", null, "GET") == 200, "GET state");
        check(ReadonlyStateServer.statusFor("/state", null, "POST") == 405, "POST blocked");
        check(ReadonlyStateServer.statusFor("/state", "action=new", "GET") == 400, "query blocked");
        for (String path : new String[] {"/action", "/card_info", "/state/extra", "/%73tate", "/"}) {
            check(ReadonlyStateServer.statusFor(path, null, "GET") == 404, "path isolation");
        }
        NoRunGuard.verify();
        System.out.println("BOOT_STATE_TEST_OK native=200 compatible=200 settling=true rng_unchanged=true");
    }
}
