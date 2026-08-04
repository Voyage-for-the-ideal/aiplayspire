package battleaimod.networking;

import battleaimod.BattleAiMod;
import battleaimod.battleai.CommandRunnerController;
import battleaimod.search.SearchProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.*;
import savestate.SaveState;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AiClient {
    private static final String HOST_IP = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int READ_TIMEOUT_MILLIS = 3000;
    public static int fileIndex = 0;
    public static volatile boolean waiting = false;
    public static volatile boolean autoReplanPending = false;
    public static String preferredCommandFilename = null;
    public static String preferredStartFilename = null;

    private final Socket socket;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AiClient() throws IOException {
        this(true);
    }

    public AiClient(boolean connect) throws IOException {
        socket = new Socket();
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);

        if (connect) {
            try {
                socket.connect(new InetSocketAddress(HOST_IP, AiServer.PORT_NUMBER), CONNECT_TIMEOUT_MILLIS);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }
    }

    public void sendState() {
        SearchProfile profile = BattleAiMod.searchProfile;
        sendState(profile.maxExpansions(), profile.timeoutMillis(), profile);
    }

    public void sendState(String readFile) {
        try {
            JsonObject response = new JsonParser()
                    .parse(Files.lines(Paths.get(readFile)).collect(Collectors.joining()))
                    .getAsJsonObject();
            updateControllerForCommands(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendState(int numTurns) {
        sendState(numTurns, BattleAiMod.searchProfile.timeoutMillis(), BattleAiMod.searchProfile);
    }

    private void sendState(int maxExpansions, long timeoutMillis, SearchProfile profile) {
        if (waiting) {
            System.err.println("Ignoring overlapping Battle AI state request");
            return;
        }

        final SaveState state = new SaveState();

        AbstractDungeon.player.hand.refreshHandLayout();
        waiting = true;
        executor.submit(() -> {
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                String encodedState = state.jsonEncode().toString();

                try {

                    String directoryName = String
                            .format("startstates/%s/%02d/%d", SeedHelper.getString(Settings.seed),
                                    AbstractDungeon.floorNum, fileIndex++);
                    File directory = new File(directoryName);
                    directory.mkdirs();

                    String fileName = directoryName + "/start.txt";
                    if (preferredStartFilename != null) {
                        fileName = preferredStartFilename;
                        preferredStartFilename = null;
                    }

                    String commandFileName = directoryName + "/commands.txt";

                    if (preferredCommandFilename != null) {
                        commandFileName = preferredCommandFilename;
                        preferredCommandFilename = null;
                    }

                    // Convert to absolute paths: the server runs in its own working directory
                    String absoluteFileName = new File(fileName).getAbsolutePath();
                    String absoluteCommandFileName = new File(commandFileName).getAbsolutePath();

                    System.err.println("writing to " + absoluteFileName);

                    try (OutputStreamWriter writer =
                                 new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8)) {
                        writer.write(encodedState);
                    }

                    JsonObject runRequest = new JsonObject();

                    runRequest.addProperty("fileName", absoluteFileName);
                    runRequest.addProperty("num_turns", maxExpansions);
                    runRequest.addProperty("max_expansions", maxExpansions);
                    runRequest.addProperty("timeout_ms", timeoutMillis);
                    runRequest.addProperty("search_profile", profile.toString());
                    runRequest.addProperty("command_file", absoluteCommandFileName);
                    runRequest.addProperty("client_cwd", System.getProperty("user.dir"));

                    out.writeUTF(runRequest.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
                BattleAiMod.rerunController = null;

                DataInputStream in = new DataInputStream(new BufferedInputStream(socket
                        .getInputStream()));

                socket.setSoTimeout(5000);

                String readLine = "";

                while (!readLine.equals(AiServer.doneString)) {
                    try {
                        readLine = in.readUTF();
                    } catch (SocketTimeoutException e) {
                        System.err.println("Server failed to respond after 5 seconds");
                        continue;
                    }

                    try {
                        JsonObject parsed = new JsonParser().parse(readLine).getAsJsonObject();
                        updateControllerForCommands(parsed);

                        if (parsed.has("metrics") && parsed.has("type") &&
                                AiServer.commandListString.equals(parsed.get("type").getAsString())) {
                            System.err.println("Search metrics " + parsed.get("metrics"));
                        }

                        if (parsed.has("message")) {
                            BattleAiMod.steveMessage = parsed.get("message").getAsString();
                        }

                    } catch (Exception e) {
                        // Not a json string
                    }
                }

                System.err.println("Received done");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Server disconnected; clearing client for reset.");
                BattleAiMod.aiClient = null;
                BattleAiMod.rerunController = null;
            } finally {
                waiting = false;
            }
        });
    }

    public void runQueriedCommands(List<Command> commandsFromServer) {
        executor.submit(() -> {
            try {
                BattleAiMod.rerunController = null;

                updateControllerForCommands(commandsFromServer);


                System.err.println("Received done");
            } catch (Exception e) {
                System.err.println("Server disconnected; clearing client for reset.");
                BattleAiMod.aiClient = null;
                BattleAiMod.rerunController = null;
            }
        });
    }

    public static Command toCommand(JsonElement jsonElement) {
        return CommandCodec.decode(jsonElement);
    }

    public static Command toCommand(String commandString) {
        return CommandCodec.decode(commandString);
    }

    private static void updateControllerForCommands(JsonObject jsonMessage) {

        JsonArray jsonCommands = null;
        if (jsonMessage.has("commands")) {
            jsonCommands = jsonMessage.get("commands").getAsJsonArray();
        } else if (jsonMessage.has("command_path")) {
            try {
                JsonObject readResponse = new JsonParser()
                        .parse(Files.lines(Paths.get(jsonMessage.get("command_path").getAsString()))
                                    .collect(Collectors.joining())).getAsJsonObject();
                jsonCommands = readResponse.get("commands").getAsJsonArray();

                System.err.println("should have read the thing, hopefully this works");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            return;
        }

        ArrayList<Command> commandsFromServer = new ArrayList<>();

        for (JsonElement jsonCommand : jsonCommands) {
            Command toAdd = toCommand(jsonCommand);
            commandsFromServer.add(toAdd);
        }

        boolean complete = jsonMessage.get("type").getAsString()
                                      .equals(AiServer.commandListString);
        if (complete) {
            autoReplanPending = jsonMessage.has("should_replan") &&
                    jsonMessage.get("should_replan").getAsBoolean();
        }
        if (BattleAiMod.rerunController == null) {
            LudicrousSpeedMod.mustRestart = false;
            LudicrousSpeedMod.controller = BattleAiMod.rerunController = new CommandRunnerController(commandsFromServer, complete);
        } else {
            BattleAiMod.rerunController
                    .updateBestPath(commandsFromServer, complete);
        }
    }

    private static void updateControllerForCommands(List<Command> commandsFromServer) {
        boolean complete = true;
        if (BattleAiMod.rerunController == null) {
            LudicrousSpeedMod.mustRestart = false;
            LudicrousSpeedMod.controller = BattleAiMod.rerunController = new CommandRunnerController(commandsFromServer, complete);
        } else {
            BattleAiMod.rerunController
                    .updateBestPath(commandsFromServer, complete);
        }
    }
}
