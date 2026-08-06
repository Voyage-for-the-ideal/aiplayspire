package ludicrousspeed.simulator.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EndCommand implements Command {
    public StateDebugInfo stateDebugInfo = null;

    private String diffStateString = null;

    public EndCommand() {
    }

    public EndCommand(String jsonString, String diffStateString) {
        try {
            JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

            if (parsed.has("state_debug_info"))
                stateDebugInfo = new StateDebugInfo(parsed.get("state_debug_info").getAsString());
            this.diffStateString = diffStateString;
        } catch (Exception e) {
            System.err.println("Exception parsing EndCommand: " + e);
            // still return a plain End Command
        }

    }

    public EndCommand(String jsonString) {
        try {
            JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

            if (parsed.has("state_debug_info"))
                stateDebugInfo = new StateDebugInfo(parsed.get("state_debug_info").getAsString());
        } catch (Exception e) {
            System.err.println("Exception parsing EndCommand: " + e);
            // still return a plain End Command
        }

    }

    @Override
    public void execute() {
        if (!StateDiffChecker.check(diffStateString, this.toString())) {
            return;
        }

        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
    }

    @Override
    public String toString() {

        String debugString = "";
        if (stateDebugInfo != null) {
            debugString = stateDebugInfo.encode();
        }

        return "EndCommand " + debugString + "\n";
    }

    @Override
    public String encode() {
        JsonObject endCommandJson = new JsonObject();

        endCommandJson.addProperty("type", "END");

        if (stateDebugInfo != null) {
            endCommandJson.addProperty("state_debug_info", stateDebugInfo.encode());
        }
        return endCommandJson.toString();
    }
}
