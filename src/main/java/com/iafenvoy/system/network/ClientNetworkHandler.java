package com.iafenvoy.system.network;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.request.ControlRequest;
import com.iafenvoy.assistant.response.AgentResponse;
import com.iafenvoy.assistant.response.CommandSuggestionsResponse;
import com.iafenvoy.assistant.util.TypeDispatch;
import com.iafenvoy.system.agent.response.BindingResponse;
import com.iafenvoy.system.agent.response.HistoryResponse;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.response.StateResponse;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.network.payload.DisplayCardsS2CPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import com.iafenvoy.system.registry.SystemResponseTypes;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.screen.gui.TokenUsageScreen;
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
    private static final Map<BindingResponse.Change, Consumer<AgentResult<CardUpdate>>> BINDING = Map.of(
            BindingResponse.Change.BOUND, ClientNetworkHandler::bound,
            BindingResponse.Change.REVOKED, ClientNetworkHandler::revoked,
            BindingResponse.Change.INVALIDATED, result -> {
                AssistantClient.clear();
                AssistantClient.notice(result);
            });
    private static final TypeDispatch<Minecraft, Void> RESPONSES = new TypeDispatch<Minecraft, Void>((_, _) -> {
        throw new IllegalArgumentException("Unregistered assistant response handler");
    }).register(StateResponse.class, (_, response) -> {
        AssistantClient.synchronizedState(response.requestId(), response.state());
        return null;
    }).register(CommandSuggestionsResponse.class, (_, response) -> {
        ClientModuleCaches.COMMAND.receive(response);
        return null;
    }).register(HistoryResponse.class, (_, response) -> {
        AssistantClient.history(response.requestId(), response.history());
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

    public static void handleDisplayCards(DisplayCardsS2CPayload payload, IPayloadContext context) {
        ClientModuleCaches.DISPLAY_CARDS.receive(payload);
    }

    public static void handleAssistantResponse(AgentResponse response) {
        if (!SystemResponseTypes.RESULTS.dispatch(response, AssistantClient::receive, AssistantClient::notice))
            RESPONSES.apply(Minecraft.getInstance(), response);
    }

    private static void bound(AgentResult<CardUpdate> result) {
        AssistantClient.notice(result);
        AssistantClient.send(ControlRequest.STATE);
    }

    private static void revoked(AgentResult<CardUpdate> result) {
        Minecraft minecraft = Minecraft.getInstance();
        AssistantClient.clear();
        if (minecraft.screen instanceof AssistantScreen || minecraft.screen instanceof AssistantSimpleScreen || minecraft.screen instanceof TokenUsageScreen
                || minecraft.screen instanceof AssistantSettingsScreen)
            minecraft.setScreen(null);
        AssistantMessages.result(result);
    }

    public static void handleTokenUsageResponse(TokenUsageResponseS2CPayload response, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (response.open()) {
            if (minecraft.screen instanceof TokenUsageScreen screen) screen.update(response);
            else minecraft.setScreen(new TokenUsageScreen(minecraft.screen, response));
        } else if (minecraft.screen instanceof TokenUsageScreen screen) screen.receive(response);
    }
}
