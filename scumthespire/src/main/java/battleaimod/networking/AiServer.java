package battleaimod.networking;

import battleaimod.BattleAiMod;
import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.StateNode;
import battleaimod.battleai.TurnNode;
import battleaimod.search.SearchProfile;
import battleaimod.search.SearchStateKey;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ludicrousspeed.LudicrousSpeedMod;
import savestate.SaveState;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AiServer {
    public static final int PORT_NUMBER = 5125;
    public static final String doneString = "DONE";

    public static final String statusUpdateString = "STATUS_UPDATE";
    public static final String commandListString = "COMMAND_LIST";
    public static final String pingString = "PING";
    public static final String pongString = "PONG";
    public static final String shutdownString = "SHUTDOWN";
    public static final String replayString = "REPLAY";

    public AiServer() {
        ThreadFactory namedThreadFactory =
                new ThreadFactoryBuilder().setNameFormat("server-networking-thread-%d").build();
        ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);
        executor.submit(() -> {
            int port = Integer.getInteger("battleai.port", PORT_NUMBER);
            try (ServerSocket serverSocket = new ServerSocket(port);
                 Socket socket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(new BufferedInputStream(socket
                         .getInputStream()));
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                System.err.println("BATTLEAI_SERVER_READY port=" + port + " testMode=" + isTestMode());
                while (!BattleAiMod.shutdownRequested) {
                    if (BattleAiMod.battleAiController == null) {
                        String requestFilePath = "";
                        String endSuffix = "/end.txt";
                        String commandFileName = null;
                        String clientCwd = null;
                        boolean statesMatch = true;
                        boolean shouldWrite = false;
                        String newContents = "";
                        try {
                            String runRequestString = in.readUTF();
                            JsonObject runRequest = new JsonParser().parse(runRequestString)
                                                                    .getAsJsonObject();

                            if (runRequest.has("type") && pingString.equals(runRequest.get("type").getAsString())) {
                                JsonObject pong = new JsonObject();
                                pong.addProperty("type", pongString);
                                pong.addProperty("test_mode", isTestMode());
                                out.writeUTF(pong.toString());
                                continue;
                            }
                            if (runRequest.has("type") && shutdownString.equals(runRequest.get("type").getAsString())) {
                                if (!isTestMode()) {
                                    throw new IllegalArgumentException("SHUTDOWN is only available in test mode");
                                }
                                BattleAiMod.shutdownRequested = true;
                                JsonObject shutdown = new JsonObject();
                                shutdown.addProperty("type", shutdownString);
                                shutdown.addProperty("accepted", true);
                                out.writeUTF(shutdown.toString());
                                continue;
                            }
                            if (runRequest.has("type") && replayString.equals(runRequest.get("type").getAsString())) {
                                handleReplay(runRequest, out);
                                continue;
                            }

                            requestFilePath = runRequest.get("fileName").getAsString();
                            clientCwd = runRequest.get("client_cwd").getAsString();
                            Path filePath = Paths.get(requestFilePath);

                            System.err.println("reading from " + requestFilePath);
                            System.err.println("filePath is " + filePath);

                            if (runRequest.has("end_suffix")) {
                                endSuffix = runRequest.get("end_suffix").getAsString();
                            }

                            if (runRequest.has("command_file")) {
                                commandFileName = runRequest.get("command_file").getAsString();
                            } else {
                                System.err.println("no command file path");
                            }

                            SaveState originalState = SaveState.forFile(filePath.toString());

                            SearchProfile profile = SearchProfile.fromString(runRequest.has("search_profile")
                                    ? runRequest.get("search_profile").getAsString() : null);
                            BattleAiMod.requestedSearchProfile = profile;
                            BattleAiMod.requestedTurnNum = runRequest.has("max_expansions")
                                    ? runRequest.get("max_expansions").getAsInt()
                                    : runRequest.get("num_turns").getAsInt();
                            BattleAiMod.requestedTimeoutMillis = runRequest.has("timeout_ms")
                                    ? runRequest.get("timeout_ms").getAsLong()
                                    : profile.timeoutMillis();
                            System.err.println("runRequest received profile=" + profile
                                    + " max_expansions=" + BattleAiMod.requestedTurnNum
                                    + " timeout_ms=" + BattleAiMod.requestedTimeoutMillis);
                            BattleAiMod.saveState = originalState;
                            BattleAiMod.saveState.initPlayerAndCardPool();

//                                System.err.println("start state equals: " + shouldWrite);
                            System.gc();

                            System.err.println("state parsed " + commandFileName);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("Failed to parse client request; clearing server for reset");
                            clearServerState();
                            return;
                        }


                        BattleAiMod.shouldStartAiFromServer = true;
                        BattleAiMod.goFast = true;

                        // let the AI start before sending out requests
                        while (BattleAiMod.battleAiController == null) {
                            try {
                                System.err.println("waiting for controller to init...");
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        System.err.println("Battle Controller Started " + commandFileName);

                        int latestWrittenTurn = 0;

                        // Still looking for route
                        while (BattleAiMod.battleAiController != null &&
                                !BattleAiMod.battleAiController.isDone()) {
                            // Send update
                            JsonObject jsonToSend = new JsonObject();
                            jsonToSend.addProperty("type", statusUpdateString);

                            TurnNode committedTurn = BattleAiMod.battleAiController.committedTurn();

                            if (BattleAiMod.battleAiController.searchProfile().streamCommands() &&
                                    committedTurn != null) {
                                int committedTurnNumber = committedTurn.startingState.saveState.turn;

                                if (latestWrittenTurn < committedTurnNumber) {
                                    latestWrittenTurn = committedTurnNumber;
                                    JsonArray currentCommands = commandsForStateNode(committedTurn.startingState, false, clientCwd);
                                    jsonToSend.add("commands", currentCommands);
                                }
                            }

                            jsonToSend.addProperty("message", String
                                    .format("%d / %d", BattleAiMod.battleAiController
                                            .metrics().expandedNodes, BattleAiMod.battleAiController
                                            .maxExpansions()));
                            jsonToSend.add("metrics", BattleAiMod.battleAiController.metrics()
                                    .jsonEncode(BattleAiMod.battleAiController.elapsedMillis(),
                                            BattleAiMod.battleAiController.stopReason()));

                            try {
                                out.writeUTF(jsonToSend.toString());
                            } catch (UTFDataFormatException e) {
                                // If the messages get too long just don't send.
                            }

                            try {
                                Thread.sleep(BattleAiMod.MESSAGE_TIME_MILLIS);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }


                        System.err.println("BattleAI finished " + commandFileName);

                        if (BattleAiMod.battleAiController != null && BattleAiMod.battleAiController
                                .isDone()) {
                            JsonObject jsonToSend = new JsonObject();
                            JsonArray commands = commandsForStateNode(BattleAiMod.battleAiController.bestEnd, true, clientCwd);

                            String endFileName = Paths.get(requestFilePath).getParent() + endSuffix;

                            try (FileWriter writer = new FileWriter(endFileName)) {
                                writer.write(BattleAiMod.battleAiController.bestEnd.saveState
                                        .jsonEncode().toString());
                            }

                            // Send Command List
                            jsonToSend.addProperty("type", commandListString);
                            jsonToSend.add("commands", commands);
                            jsonToSend.addProperty("battle_complete",
                                    BattleAiMod.battleAiController.battleComplete());
                            jsonToSend.addProperty("should_replan",
                                    BattleAiMod.battleAiController.shouldReplan());
                            jsonToSend.addProperty("stop_reason",
                                    BattleAiMod.battleAiController.stopReason());
                            jsonToSend.addProperty("final_state_key", SearchStateKey
                                    .fromSaveState(BattleAiMod.battleAiController.bestEnd.saveState).toString());
                            jsonToSend.addProperty("state_key_algorithm", SearchStateKey.algorithm());
                            jsonToSend.add("metrics", BattleAiMod.battleAiController.metrics()
                                    .jsonEncode(BattleAiMod.battleAiController.elapsedMillis(),
                                            BattleAiMod.battleAiController.stopReason()));
                            jsonToSend
                                    .addProperty("predictor_damage", BattleAiMod.battleAiController.expectedDamage);

                            if (commandFileName != null) {
                                try {
                                    System.err
                                            .println("should be writing file to " + commandFileName);
                                    Path parent = Paths.get(commandFileName).getParent();
                                    new File(parent.toString()).mkdirs();
                                    try (FileWriter commandWriter = new FileWriter(commandFileName)) {
                                        commandWriter.write(jsonToSend.toString());
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            try {
                                out.writeUTF(jsonToSend.toString());
                            } catch (UTFDataFormatException e) {
                                System.err
                                        .println("Result too big, removing commands and writing to file instead");
                                jsonToSend.remove("commands");

                                if (commandFileName != null) {
                                    System.err.println("returning with command path");
                                    jsonToSend.addProperty("command_path", commandFileName);
                                    out.writeUTF(jsonToSend.toString());
                                } else {
                                    System.err.println("commandFileName is null");
                                }
                            }
                            LudicrousSpeedMod.controller = BattleAiMod.battleAiController = null;
                        } else {
                            System.err.println("This shouldn't have happened");
                        }

                        System.err.println("Sending done");
                        out.writeUTF(doneString);
                    }
                }


            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Client Disconnected, clearing server for reset");
                clearServerState();
            }
        });
    }

    private static void clearServerState() {
        BattleAiMod.aiServer = null;
        BattleAiMod.shouldStartAiFromServer = false;
        BattleAiMod.battleAiController = null;
        LudicrousSpeedMod.controller = null;
    }

    private static boolean isTestMode() {
        return Boolean.getBoolean("battleai.testMode");
    }

    private static void handleReplay(JsonObject request, DataOutputStream out) throws IOException {
        if (!isTestMode()) {
            throw new IllegalArgumentException("REPLAY is only available in test mode");
        }
        SaveState start = SaveState.forFile(request.get("fileName").getAsString());
        start.initPlayerAndCardPool();
        String baseDirectory = request.has("client_cwd") ? request.get("client_cwd").getAsString() : ".";
        ArrayList<ludicrousspeed.simulator.commands.Command> commands = new ArrayList<>();
        for (com.google.gson.JsonElement encoded : request.getAsJsonArray("commands")) {
            if (!encoded.isJsonNull() && encoded.getAsJsonObject().has("state")) {
                JsonObject copy = encoded.getAsJsonObject();
                copy.addProperty("state", Paths.get(baseDirectory, copy.get("state").getAsString()).toString());
                commands.add(CommandCodec.decode(copy));
            } else {
                commands.add(CommandCodec.decode(encoded));
            }
        }
        BattleAiMod.replayStartState = start;
        BattleAiMod.replayCommands = commands;
        BattleAiMod.shouldStartReplay = true;

        long deadline = System.currentTimeMillis() + request.get("timeout_ms").getAsLong();
        while (BattleAiMod.replayController == null && System.currentTimeMillis() < deadline) {
            sleepBriefly();
        }
        while (BattleAiMod.replayController != null && !BattleAiMod.replayController.isDone() &&
                System.currentTimeMillis() < deadline) {
            sleepBriefly();
        }

        JsonObject response = new JsonObject();
        response.addProperty("type", replayString);
        if (BattleAiMod.replayController == null || !BattleAiMod.replayController.isDone()) {
            response.addProperty("error", "replay timed out before the game update completed");
            response.addProperty("commands_executed", 0);
            response.addProperty("diff_valid", false);
        } else {
            response.addProperty("final_state_key", BattleAiMod.replayController.finalStateKey());
            response.addProperty("state_key_algorithm", SearchStateKey.algorithm());
            response.addProperty("commands_executed", BattleAiMod.replayController.commandsExecuted());
            response.addProperty("error", BattleAiMod.replayController.error());
            response.addProperty("diff_valid", BattleAiMod.replayController.error() == null);
        }
        out.writeUTF(response.toString());
        BattleAiMod.replayController = null;
        LudicrousSpeedMod.controller = null;
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static JsonArray commandsForStateNode(StateNode root, boolean shouldPrint, String clientCwd) {
        JsonArray commands = new JsonArray();
        Path planDirectory;
        try {
            planDirectory = ReplaySnapshotStore.createPlanDirectory(clientCwd);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create replay snapshot directory", e);
        }

        List<StateNode> stateNodes = BattleAiController.stateNodesToGetToNode(root);

        // Print the best path for debugging
        Iterator<StateNode> printIterator = stateNodes.iterator();

        while (printIterator.hasNext() && shouldPrint) {
            StateNode stateNode = printIterator.next();
            System.err.print(stateNode.lastCommand + " ");
        }

        Iterator<StateNode> bestPath = stateNodes.iterator();

        String stateDiffString = null;
        while (bestPath.hasNext()) {
            StateNode stateNode = bestPath.next();

            if (stateNode != null && stateNode.lastCommand != null) {
                JsonObject command = new JsonObject();

                command.addProperty("command", stateNode.lastCommand.encode());
                if (stateDiffString != null) {
                    try {
                        Path stateFile = planDirectory.resolve(commands.size() + ".txt");
                        String relativeStatePath = Paths.get(ReplaySnapshotStore.ROOT_DIRECTORY)
                                                          .resolve(planDirectory.getFileName())
                                                          .resolve(stateFile.getFileName())
                                                          .toString()
                                                          .replace(File.separatorChar, '/');

                        try (OutputStreamWriter writer = new OutputStreamWriter(
                                new FileOutputStream(stateFile.toFile()), StandardCharsets.UTF_8)) {
                            writer.write(stateDiffString);
                        }

                        command.addProperty("state", relativeStatePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                stateDiffString = stateNode.saveState.diffEncode();
                commands.add(command);
            } else {
                if (stateNode != null && stateNode.saveState != null) {
                    stateDiffString = stateNode.saveState.diffEncode();
                }
                commands.add(JsonNull.INSTANCE);
            }
        }


        return commands;
    }
}
