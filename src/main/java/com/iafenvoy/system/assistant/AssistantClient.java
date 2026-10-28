package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.request.ChatRequest;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.request.InvokeRequest;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.client.AssistantClientCommands;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-thread navigation and effects; conversation updates replace one immutable state object.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClient {
    private static AssistantClientState state = AssistantClientState.EMPTY;
    // This view invalidation counter stays monotonic across EMPTY resets.
    private static long revision;

    private AssistantClient() {
    }

    public static AssistantClientState state() {
        return state;
    }

    public static SystemDefinition system() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? SystemDefinition.DEFAULT : state.serverState().system()
                .flatMap(id -> SystemDataPackRegistries.get(connection.registryAccess(), id)).orElse(SystemDefinition.DEFAULT);
    }

    public static long revision() {
        return revision;
    }

    private record ViewOwner(Object screen, String page) {
    }

    private static ViewOwner viewOwner() {
        Screen screen = Minecraft.getInstance().screen;
        return new ViewOwner(screen, screen instanceof AssistantScreen full ? full.page() : "");
    }

    public static boolean simpleMode() {
        return ClientConfig.INSTANCE.get().simpleMode();
    }

    public static void configureMode(boolean simple) {
        ClientConfig.INSTANCE.set(new ClientConfig(simple));
    }

    public static void open(boolean simple, String draft) {
        configureMode(simple);
        Minecraft.getInstance().setScreen(simple ? new AssistantSimpleScreen(draft) : new AssistantScreen(draft == null ? "" : draft));
    }

    public static boolean submitText(String text) {
        if (text == null || text.isBlank() || state.busy()) return false;
        if (text.codePointCount(0, text.length()) > 2000) {
            local(AgentResult.error("INVALID_INPUT"));
            return true;
        }
        if (!AssistantClientCommands.execute(text)) send(new ChatRequest(text.strip()));
        return true;
    }

    public static void local(AgentResult result) {
        result.cards().forEach(card -> ClientCards.apply(card, false));
        state = state.local(result);
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
        state = state.withoutPending();
        revision++;
    }

    public static void complete(String requestId) {
        AssistantClientState completed = state.complete(requestId);
        if (completed == state) return;
        state = completed;
        revision++;
    }

    private static void receive(String requestId, AgentResult result, boolean notification) {
        AssistantClientState.Pending pending = state.pending().get(requestId);
        if (!notification && pending == null) {
            // Timed-out replies may finish an existing card, but cannot select a page or create a new message.
            result = result.withPresentation(result.messageKey(), result.text(), result.cards().stream()
                    .filter(card -> card.mode() != CardUpdate.Mode.SHOW).toList(), true);
        }
        boolean select = pending != null && pending.owner().equals(viewOwner());
        boolean removesCurrent = state.current() != null && state.current().result() != null && result.cards().stream()
                .anyMatch(update -> update.mode() == CardUpdate.Mode.REMOVE && state.current().result().cards().stream()
                        .anyMatch(card -> card.ref().id().equals(update.ref().id())));
        if (removesCurrent && Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
            screen.stopSpeech();
            screen.stopRecognition();
        }
        List<CardUpdate> updates = result.cards().stream().filter(card -> ClientCards.apply(card, false)).toList();
        AgentResult accepted = result.withPresentation(result.messageKey(), result.text(), updates, result.silent());
        state = state.receive(requestId, accepted, notification, select);
        revision++;
        AssistantMessages.refresh(accepted);
        if (accepted.silent() || updates.stream().noneMatch(card -> card.mode() == CardUpdate.Mode.SHOW)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (notification || select && !(minecraft.screen instanceof AssistantInputScreen))
            AssistantMessages.result(accepted);
        else if (select && minecraft.screen instanceof AssistantInputScreen screen) screen.speech().result(accepted);
    }

    public static void synchronizedState(String requestId, ResultTypes.ClientState value) {
        if (!requestId.isEmpty() && !state.pending().containsKey(requestId)) return;
        state = state.synchronizedState(requestId, value);
        revision++;
    }

    public static void history(String requestId, ResultTypes.History value) {
        if (!state.pending().containsKey(requestId)) return;
        state = state.history(requestId, value);
        revision++;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, AssistantClientState.Pending> pending : state.pending().entrySet())
            if (pending.getValue().deadline() <= now) complete(pending.getKey());
    }

    public static void send(AgentAction call, String message) {
        send(new InvokeRequest(call, message));
    }

    public static void send(AgentRequest request) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || state.busy()) return;
        String id = UUID.randomUUID().toString();
        if (request != ControlRequest.STATE && Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
            screen.stopSpeech();
            screen.stopRecognition();
        }
        state = state.requested(id, request, viewOwner());
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
        ClientCards.clear();
        state = AssistantClientState.EMPTY;
        revision++;
    }
}
