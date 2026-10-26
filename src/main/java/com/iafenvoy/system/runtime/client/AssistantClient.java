package com.iafenvoy.system.runtime.client;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.command.client.AssistantClientCommands;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import com.iafenvoy.system.util.TypeDispatch;
import com.iafenvoy.system.util.codec.CodecIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.*;
import java.util.function.Function;

/**
 * Client-thread conversation state, local navigation and request tracking; wire decoding lives in the network handler.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClient {
    private static final List<ChatEntry> ENTRIES = new ArrayList<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static long revision;
    private static boolean saveHistory = true;
    private static boolean externalModel = true;
    private static boolean questAvailable;
    private static List<String> commands = List.of();
    private static ChatEntry current;
    private static final TypeDispatch<Void, Void> CARD_UPDATES = new TypeDispatch<Void, Void>((_, _) -> null)
            .register(ResultTypes.ClientState.class, (_, state) -> {
                saveHistory = state.historySaved();
                externalModel = state.externalModel();
                questAvailable = state.questAvailable();
                commands = state.commands();
                return null;
            }).register(ResultTypes.Preferences.class, (_, preferences) -> {
                saveHistory = preferences.historySaved();
                externalModel = preferences.externalModel();
                return null;
            }).register(ResultTypes.SignInStatus.class, (_, status) -> {
                saveHistory = status.historySaved();
                externalModel = status.externalModel();
                return null;
            });
    private static final Map<String, Function<ResultContext, Boolean>> RESULT_HANDLERS = Map.of(
            "system.result.state", _ -> false,
            "system.result.forgotten", context -> {
                ENTRIES.clear();
                return append(context);
            },
            "system.result.history", AssistantClient::restoreHistory);

    private record ResultContext(AgentResult result, boolean pending, boolean notification) {
    }

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
        return ClientConfig.INSTANCE.get().simpleMode();
    }

    public static List<AgentResult.Action> currentActions() {
        return current == null || current.result() == null ? List.of()
                : current.result().cards().stream().flatMap(card -> card.actions().stream()).toList();
    }

    public static void configureMode(boolean simple) {
        ClientConfig.INSTANCE.set(new ClientConfig(simple));
    }

    public static void open(boolean simple, String draft) {
        configureMode(simple);
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
        receive(requestId, result, false);
    }

    public static void notice(AgentResult result) {
        receive("", result, true);
    }

    public static void invalidatePending() {
        PENDING.clear();
    }

    public static void complete(String requestId) {
        if (PENDING.remove(requestId)) revision++;
    }

    private static void receive(String requestId, AgentResult result, boolean notification) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pending = PENDING.remove(requestId);
        for (AgentResult.Card card : result.cards()) CARD_UPDATES.apply(null, card.data().value());
        if (!RESULT_HANDLERS.getOrDefault(result.messageKey(), AssistantClient::append).apply(new ResultContext(result, pending, notification))) {
            revision++;
            return;
        }
        while (ENTRIES.size() > 64) ENTRIES.removeFirst();
        revision++;
        if (notification || !(minecraft.screen instanceof AssistantScreen) && !(minecraft.screen instanceof AssistantSimpleScreen))
            AssistantMessages.result(result, !result.messageKey().equals("system.error.not_bound"));
        else if (minecraft.screen instanceof AssistantInputScreen screen) screen.speech().result(result);
    }

    private static boolean append(ResultContext context) {
        ENTRIES.add(new ChatEntry(false, "", context.result()));
        if (!context.notification() && (context.pending() || !busy()))
            current = new ChatEntry(false, context.pending() && current != null ? current.text() : "", context.result());
        return true;
    }

    private static boolean restoreHistory(ResultContext context) {
        ENTRIES.clear();
        for (AgentResult.Card card : context.result().cards()) {
            if (!(card.data().value() instanceof ResultTypes.History(List<PlayerData.Message> messages))) continue;
            for (PlayerData.Message message : messages) {
                String text = message.text();
                if (message.role().equals("user")) ENTRIES.add(new ChatEntry(true, text, null));
                else {
                    try {
                        ENTRIES.add(new ChatEntry(false, "", CodecIO.parse(AgentResult.CODEC, text)));
                    } catch (RuntimeException error) {
                        ENTRIES.add(new ChatEntry(false, text, null));
                    }
                }
            }
        }
        return true;
    }

    public static void send(AgentResult.Action action, String message) {
        send(action.call(), message);
    }

    public static void send(TypedPayload<?> call, String message) {
        send(new AgentRequest.Invoke(call, message));
    }

    public static void send(AgentRequest request) {
        String message = request.text();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !PENDING.isEmpty()) return;
        String id = UUID.randomUUID().toString();
        if (!message.isBlank()) ENTRIES.add(new ChatEntry(true, message, null));
        if (request != AgentRequest.Control.STATE) {
            if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
                screen.stopSpeech();
                screen.stopRecognition();
            }
            current = new ChatEntry(false, message, null);
        }
        PENDING.add(id);
        revision++;
        connection.send(new AssistantRequestC2SPayload(id, request));
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
