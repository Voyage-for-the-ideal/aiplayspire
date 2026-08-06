package battleaimod.networking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ludicrousspeed.simulator.commands.CardCommand;
import ludicrousspeed.simulator.commands.CardRewardSelectCommand;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.EndCommand;
import ludicrousspeed.simulator.commands.GridSelectCommand;
import ludicrousspeed.simulator.commands.GridSelectConfirmCommand;
import ludicrousspeed.simulator.commands.HandSelectCommand;
import ludicrousspeed.simulator.commands.HandSelectConfirmCommand;
import ludicrousspeed.simulator.commands.PotionCommand;

/** Decodes the wire representation shared by the client and test replay endpoint. */
public final class CommandCodec {
    private CommandCodec() {
    }

    public static Command decode(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        }
        JsonObject envelope = jsonElement.getAsJsonObject();
        String commandString = envelope.get("command").getAsString();
        String statePath = envelope.has("state") ? envelope.get("state").getAsString() : null;
        return decode(commandString, statePath);
    }

    public static Command decode(String commandString) {
        return decode(commandString, null);
    }

    private static Command decode(String commandString, String statePath) {
        JsonObject command = new JsonParser().parse(commandString).getAsJsonObject();
        String type = command.get("type").getAsString();
        if ("CARD".equals(type)) {
            return statePath == null ? new CardCommand(commandString) : new CardCommand(commandString, statePath);
        }
        if ("POTION".equals(type)) {
            return statePath == null ? new PotionCommand(commandString) : new PotionCommand(commandString, statePath);
        }
        if ("END".equals(type)) {
            return statePath == null ? new EndCommand(commandString) : new EndCommand(commandString, statePath);
        }
        if ("HAND_SELECT".equals(type)) {
            return statePath == null ? new HandSelectCommand(commandString) : new HandSelectCommand(commandString, statePath);
        }
        if ("HAND_SELECT_CONFIRM".equals(type)) {
            return statePath == null ? HandSelectConfirmCommand.INSTANCE : new HandSelectConfirmCommand(statePath);
        }
        if ("GRID_SELECT".equals(type)) {
            return statePath == null ? new GridSelectCommand(commandString) : new GridSelectCommand(commandString, statePath);
        }
        if ("GRID_SELECT_CONFIRM".equals(type)) {
            return statePath == null ? GridSelectConfirmCommand.INSTANCE : new GridSelectConfirmCommand(statePath);
        }
        if ("CARD_REWARD_SELECT".equals(type)) {
            return statePath == null ? new CardRewardSelectCommand(commandString) : new CardRewardSelectCommand(commandString, statePath);
        }
        throw new IllegalArgumentException("unknown command type: " + type);
    }
}
