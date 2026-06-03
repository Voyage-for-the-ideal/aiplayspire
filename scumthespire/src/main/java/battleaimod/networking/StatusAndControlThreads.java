package battleaimod.networking;

import battleaimod.BattleAiMod;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.EscapeAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.patches.ServerStartupPatches;
import savestate.SaveStateMod;
import savestate.patches.SavesPatches;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class StatusAndControlThreads {
    private static final int SERVER_GAME_PORT = 5124;
    private static final int CLIENT_GAME_PORT = 5200;

    @SpirePatch(clz = ServerStartupPatches.GameStartupPatch.class, method = "afterStart")
    public static class StartStatusServer {
        @SpirePostfixPatch
        public static void afterAfterStart(CardCrawlGame game) {
            if (BattleAiMod.isServer) {
                startServerStatusThread();
            } else {
                startClientThread();
            }
        }
    }

    //  The server thread starts up and just sends ready signals as soon as it's up
    private static void startServerStatusThread() {
        new Thread(() -> {
            try {
                Thread.sleep(5_000);

                System.out.println("Starting status thread");
                try (ServerSocket serverGameServerSocket = new ServerSocket(SERVER_GAME_PORT);
                     Socket serverGameSocket = serverGameServerSocket.accept();
                     DataInputStream serverInputStream = new DataInputStream(new BufferedInputStream(serverGameSocket
                             .getInputStream()));
                     DataOutputStream serverOutputStream = new DataOutputStream(serverGameSocket
                             .getOutputStream())) {
                    while (true) {
                        System.out.println("Waiting for game to start...");

                        String clientRequest = serverInputStream.readUTF();

                        JsonObject response = new JsonObject();

                        boolean controllerRunning = BattleAiMod.battleAiController != null;

                        response.addProperty("yay", "yay?");
                        response.addProperty("ready", !controllerRunning);

                        serverOutputStream.writeUTF(response.toString());
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // The Client thread response to requests to load a save and or start the ai
    private static void startClientThread() {
        new Thread(() -> {
            try {
                Thread.sleep(5_000);

                System.out.println("Starting client thread");
                try (ServerSocket serverGameServerSocket = new ServerSocket(CLIENT_GAME_PORT)) {
                    while (true) {
                        try (Socket serverGameSocket = serverGameServerSocket.accept();
                             DataInputStream serverInputStream = new DataInputStream(new BufferedInputStream(serverGameSocket
                                     .getInputStream()));
                             DataOutputStream serverOutputStream = new DataOutputStream(serverGameSocket
                                     .getOutputStream())) {
                            handleClientRequest(serverInputStream, serverOutputStream);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleClientRequest(DataInputStream serverInputStream, DataOutputStream serverOutputStream)
            throws IOException {
        String clientRequest = serverInputStream.readUTF();

        JsonObject parsedRequest = new JsonParser().parse(clientRequest)
                                                   .getAsJsonObject();

        String command = parsedRequest.get("command").getAsString();

        if (command.equals("load")) {
            String path = parsedRequest.get("path").getAsString();
            AbstractPlayer.PlayerClass playerClass = AbstractPlayer.PlayerClass
                    .valueOf(parsedRequest.get("playerClass").getAsString());

            SaveStateMod.curFloorToDisplay = parsedRequest.get("replay_floor_start")
                                                          .getAsInt();
            SaveStateMod.lastFloorToDisplay = parsedRequest.get("replay_floor_end")
                                                           .getAsInt();

            if (parsedRequest.has("recall_mode")) {
                SavesPatches.recallMode = parsedRequest.get("recall_mode")
                                                       .getAsBoolean();
            }

            SavesPatches.load(path, playerClass);

            writeReadyResponse(serverOutputStream);
        } else if (command.equals("startAi")) {
            if (parsedRequest.has("command_file_out")) {
                AiClient.preferredCommandFilename = parsedRequest
                        .get("command_file_out")
                        .getAsString();
            }

            if (parsedRequest.has("start_file")) {
                AiClient.preferredStartFilename = parsedRequest.get("start_file")
                                                               .getAsString();
            }

            if (canSendState()) {
                if (BattleAiMod.aiClient == null) {
                    try {
                        BattleAiMod.aiClient = new AiClient();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (BattleAiMod.aiClient != null) {
                    BattleAiMod.aiClient.sendState();
                }
            }

            writeReadyResponse(serverOutputStream);
        } else if (command.equals("status")) {
            boolean isReady = canSendState();

            boolean escaping = false;
            if (AbstractDungeon.actionManager != null) {
                if (AbstractDungeon.actionManager.currentAction instanceof EscapeAction) {
                    escaping = true;
                }
            }

            if (isReady) {
                serverOutputStream.writeUTF("READY");
            } else {
                if (isControllerRunning() || AiClient.waiting || escaping) {
                    serverOutputStream.writeUTF("PROCESSING");
                } else {
                    serverOutputStream.writeUTF("LOADING");
                }
            }
        }
    }

    private static void writeReadyResponse(DataOutputStream serverOutputStream) throws IOException {
        JsonObject response = new JsonObject();

        boolean controllerRunning = BattleAiMod.battleAiController != null;

        response.addProperty("yay", "yay?");
        response.addProperty("ready", !controllerRunning);

        serverOutputStream.writeUTF(response.toString());
    }

    static boolean canSendState() {
        return !isControllerRunning() &&
                BattleClientController.readyForUpdate() &&
                AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER &&
                AbstractDungeon.overlayMenu.endTurnButton.enabled;
    }

    static boolean isControllerRunning() {
        return BattleAiMod.rerunController != null && !BattleAiMod.rerunController.isDone && !AbstractDungeon
                .getCurrRoom().isBattleOver;
    }
}
