package savestate.powers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PowerStateDiffEncodingTest {
    @Test
    public void diffEncodingIncludesSubclassJsonFieldsForFreshAndRestoredStates() {
        ExtendedPowerState fresh = new ExtendedPowerState(false);

        String freshDiff = fresh.diffEncode();
        JsonObject freshJson = new JsonParser().parse(freshDiff).getAsJsonObject();
        ExtendedPowerState restored = new ExtendedPowerState(freshJson);

        assertFalse(freshJson.get("just_applied").getAsBoolean());
        assertEquals(freshDiff, restored.diffEncode());
    }

    private static final class ExtendedPowerState extends PowerState {
        private final boolean justApplied;

        private ExtendedPowerState(boolean justApplied) {
            super(baseJson());
            this.justApplied = justApplied;
        }

        private ExtendedPowerState(JsonObject json) {
            super(json);
            this.justApplied = json.get("just_applied").getAsBoolean();
        }

        @Override
        public JsonObject jsonEncode() {
            JsonObject result = super.jsonEncode();
            result.addProperty("just_applied", justApplied);
            return result;
        }

        private static JsonObject baseJson() {
            JsonObject json = new JsonObject();
            json.addProperty("power_id", "Weakened");
            json.addProperty("amount", 2);
            return json;
        }
    }
}
