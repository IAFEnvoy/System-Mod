package com.iafenvoy.system.runtime.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.command.client.AssistantClientCommands;
import com.iafenvoy.system.config.ClientSettings;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.SystemConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-thread conversation state, local navigation and request tracking; wire decoding lives in the network handler. */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClient {
    private static final List<ChatEntry> ENTRIES = new ArrayList<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static long revision;
    private static boolean saveHistory = true;
    private static boolean externalModel = true;
    private static boolean questAvailable;
    private static List<String> commands = List.of();
    private static boolean simpleMode;
    private static boolean modeLoaded;
    private static ChatEntry current;

    private AssistantClient() {
    }

    public record ChatEntry(boolean player, String text, AgentResult result) {
    }

    public static List<ChatEntry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static long revision() {
        return revision;
    }

    public static boolean busy() {
        return !PENDING.isEmpty();
    }

    public static boolean saveHistory() {
        return saveHistory;
    }

    public static boolean externalModel() {
        return externalModel;
    }

    public static boolean questAvailable() {
        return questAvailable;
    }

    public static List<String> commands() {
        return commands;
    }

    public static ChatEntry current() {
        return current;
    }

    public static boolean simpleMode() {
        loadMode();
        return simpleMode;
    }

    public static List<AgentResult.Action> currentActions() {
        return current == null || current.result() == null ? List.of()
                : current.result().cards().stream().flatMap(card -> card.actions().stream()).toList();
    }

    private static void loadMode() {
        if (modeLoaded) return;
        modeLoaded = true;
        try {
            Path path = SystemConfig.file("system-client.json");
            if (Files.exists(path))
                simpleMode = CodecIO.read(ClientSettings.CODEC, path, 1024).simpleMode();
        } catch (Exception e) {
            SystemMod.LOGGER.warn("Unable to read assistant view preference", e);
        }
    }

    public static void configureMode(boolean simple) throws IOException {
        loadMode();
        if (simpleMode == simple) return;
        CodecIO.save(ClientSettings.CODEC, SystemConfig.file("system-client.json"), new ClientSettings(simple));
        simpleMode = simple;
    }

    public static void open(boolean simple, String draft) {
        try {
            configureMode(simple);
        } catch (IOException error) {
            SystemMod.LOGGER.warn("Unable to save assistant view preference", error);
        }
        Minecraft.getInstance().setScreen(simple ? new AssistantSimpleScreen(draft) : new AssistantScreen(draft == null ? "" : draft));
    }

    public static boolean submitText(String text) {
        if (text == null || text.isBlank() || busy()) return false;
        if (text.codePointCount(0, text.length()) > 2000) {
            local(AgentResult.error("INVALID_INPUT"));
            return true;
        }
        if (!AssistantClientCommands.execute(text)) send(new AgentRequest.Chat(text.strip()));
        return true;
    }

    public static void local(AgentResult result) {
        current = new ChatEntry(false, "", result);
        ENTRIES.add(current);
        while (ENTRIES.size() > 64) ENTRIES.removeFirst();
        revision++;
        if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.speech().result(result);
    }

    public static void command(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand("system " + command);
    }

    public static void receive(String requestId, AgentResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (requestId.equals("revoke_failed")) PENDING.clear();
        boolean pending = PENDING.remove(requestId);
        boolean notification = Set.of("event", "notice", "revoke_failed").contains(requestId);
        for (AgentResult.Card card : result.cards()) {
            switch (card.data().value()) {
                case ResultTypes.ClientState state -> {
                    saveHistory = state.historySaved();
                    externalModel = state.externalModel();
                    questAvailable = state.questAvailable();
                    commands = state.commands();
                }
                case ResultTypes.Preferences preferences -> {
                    saveHistory = preferences.historySaved();
                    externalModel = preferences.externalModel();
                }
                case ResultTypes.SignInStatus status -> {
                    saveHistory = status.historySaved();
                    externalModel = status.externalModel();
                }
                default -> { }
            }
        }
        if (result.messageKey().equals("system.result.state")) {
            revision++;
            return;
        }
        if (result.messageKey().equals("system.result.forgotten")) ENTRIES.clear();
        if (result.messageKey().equals("system.result.history")) {
            ENTRIES.clear();
            for (AgentResult.Card card : result.cards()) {
                if (!(card.data().value() instanceof ResultTypes.History(List<PlayerData.Message> messages))) continue;
                for (PlayerData.Message message : messages) {
                    String text = message.text();
                    if (message.role().equals("user")) ENTRIES.add(new ChatEntry(true, text, null));
                    else {
                        try {
                            ENTRIES.add(new ChatEntry(false, "", CodecIO.parse(AgentResult.CODEC, text, 1_048_576)));
                        } catch (RuntimeException error) {
                            ENTRIES.add(new ChatEntry(false, text, null));
                        }
                    }
                }
            }
        } else {
            ENTRIES.add(new ChatEntry(false, "", result));
            if (!notification && (pending || !busy()))
                current = new ChatEntry(false, pending && current != null ? current.text() : "", result);
        }
        while (ENTRIES.size() > 64) ENTRIES.removeFirst();
        revision++;
        if (notification || !(minecraft.screen instanceof AssistantScreen) && !(minecraft.screen instanceof AssistantSimpleScreen))
            AssistantMessages.result(result, !result.messageKey().equals("system.error.not_bound"));
        else if (minecraft.screen instanceof AssistantInputScreen screen) screen.speech().result(result);
    }

    public static void send(AgentResult.Action action, String message) {
        send(action.call(), message);
    }

    public static void send(TypedPayload<?> call, String message) {
        send(new AgentRequest.Invoke(call, message));
    }

    public static void send(AgentRequest request) {
        String module = request.mode(), message = request.text();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !PENDING.isEmpty()) return;
        String id = UUID.randomUUID().toString();
        if (!message.isBlank()) ENTRIES.add(new ChatEntry(true, message, null));
        if (!module.equals("state")) {
            if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
                screen.stopSpeech();
                screen.stopRecognition();
            }
            current = new ChatEntry(false, message, null);
        }
        PENDING.add(id);
        revision++;
        connection.send(new AssistantRequestC2SPayload(id, module, request.encodedInput(), message));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void clear() {
        AssistantSpeech.stop();
        if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.stopRecognition();
        AssistantMessages.clear();
        ENTRIES.clear();
        PENDING.clear();
        current = null;
        saveHistory = true;
        externalModel = true;
        questAvailable = false;
        commands = List.of();
        revision++;
    }
}
