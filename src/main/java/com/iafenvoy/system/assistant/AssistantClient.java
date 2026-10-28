package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.command.client.AssistantClientCommands;
import com.iafenvoy.system.config.file.ClientConfig;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.registry.SystemDefinitions;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.tts.AssistantSpeech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

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
        return connection == null ? SystemDefinition.DEFAULT : SystemDefinitions.current(connection.registryAccess());
    }

    public static long revision() {
        return revision;
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
        if (!AssistantClientCommands.execute(text)) send(new AgentRequest.Chat(text.strip()));
        return true;
    }

    public static void local(AgentResult result) {
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
        if (result.silent()) {
            state = state.silent(requestId, result);
            revision++;
            AssistantMessages.refresh(result);
            return;
        }
        state = state.receive(requestId, result, notification);
        revision++;
        if (result.messageKey().equals("system.result.state")) return;
        Minecraft minecraft = Minecraft.getInstance();
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
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || state.busy()) return;
        String id = UUID.randomUUID().toString();
        if (request != AgentRequest.Control.STATE && Minecraft.getInstance().screen instanceof AssistantInputScreen screen) {
            screen.stopSpeech();
            screen.stopRecognition();
        }
        state = state.requested(id, request);
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
        state = AssistantClientState.EMPTY;
        revision++;
    }
}
