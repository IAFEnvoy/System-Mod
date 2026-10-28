package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.response.BindingResponse;
import com.iafenvoy.system.agent.response.NoticeResponse;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.response.ReplyResponse;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.binding.BindingClientModule;
import com.iafenvoy.system.network.payload.AssistantResponseS2CPayload;
import com.iafenvoy.system.network.payload.QuestNarrationResponseS2CPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.screen.gui.TokenUsageScreen;
import com.iafenvoy.system.speech.tts.QuestSpeechClient;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Client payload dispatch and decoding; views and local conversation state stay in the client layer.
 */
public final class ClientNetworkHandler {
    private static final Map<OpenResponse.View, BooleanSupplier> VIEWS = Map.of(
            OpenResponse.View.CURRENT, AssistantClient::simpleMode,
            OpenResponse.View.SIMPLE, () -> true,
            OpenResponse.View.FULL, () -> false);
    private static final Map<BindingResponse.Change, Consumer<AgentResult>> BINDING = Map.of(
            BindingResponse.Change.BOUND, ClientNetworkHandler::bound,
            BindingResponse.Change.REVOKED, ClientNetworkHandler::revoked,
            BindingResponse.Change.INVALIDATED, result -> {
                AssistantClient.invalidatePending();
                AssistantClient.notice(result);
            });
    private static final TypeDispatch<Minecraft, Void> RESPONSES = new TypeDispatch<Minecraft, Void>((_, _) -> {
        throw new IllegalArgumentException("Unregistered assistant response handler");
    }).register(ReplyResponse.class, (_, reply) -> {
        AssistantClient.receive(reply.requestId(), reply.result());
        return null;
    }).register(NoticeResponse.class, (_, notice) -> {
        AssistantClient.notice(notice.result());
        return null;
    }).register(OpenResponse.class, (_, open) -> {
        open.requestId().ifPresent(AssistantClient::complete);
        AssistantClient.open(VIEWS.get(open.view()).getAsBoolean(), "");
        AssistantClient.send(ControlRequest.STATE);
        return null;
    }).register(BindingResponse.class, (_, binding) -> {
        BINDING.get(binding.change()).accept(binding.result());
        return null;
    });

    private ClientNetworkHandler() {
    }

    public static void handleAssistantResponse(AssistantResponseS2CPayload response, IPayloadContext context) {
        RESPONSES.apply(Minecraft.getInstance(), response.response());
    }

    public static void handleQuestNarrationResponse(QuestNarrationResponseS2CPayload response, IPayloadContext context) {
        QuestSpeechClient.receive(response);
    }

    private static void bound(AgentResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        AssistantClient.local(result);
        BindingClientModule.bound(minecraft.player == null ? "" : minecraft.player.getName().getString());
        AssistantClient.send(ControlRequest.STATE);
    }

    private static void revoked(AgentResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        AssistantClient.clear();
        if (minecraft.screen instanceof AssistantScreen || minecraft.screen instanceof AssistantSimpleScreen || minecraft.screen instanceof TokenUsageScreen
                || minecraft.screen instanceof AssistantSettingsScreen)
            minecraft.setScreen(null);
        AssistantMessages.result(result, false);
    }

    public static void handleTokenUsageResponse(TokenUsageResponseS2CPayload response, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (response.open()) {
            if (minecraft.screen instanceof TokenUsageScreen screen) screen.update(response);
            else minecraft.setScreen(new TokenUsageScreen(minecraft.screen, response));
        } else if (minecraft.screen instanceof TokenUsageScreen screen) screen.receive(response);
    }
}
