package com.iafenvoy.system.network;

import com.iafenvoy.assistant.network.AssistantChannel;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.network.payload.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;

/**
 * Wire registration only; processing and connection state belong to the side handlers.
 */
@EventBusSubscriber
public final class NetworkManager {
    public static final AssistantChannel<ItemSearchBatch> ASSISTANT = new AssistantChannel<>(SystemMod.MOD_ID, ItemSearchBatch.STREAM_CODEC);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("7");
        ASSISTANT.register(registrar, ServerNetworkHandler::handleAssistantRequest, ServerNetworkHandler::assistantError);
        registrar
                .playToServer(TokenUsageRequestC2SPayload.TYPE, TokenUsageRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleTokenUsageRequest))
                .playToServer(DisplayCardC2SPayload.TYPE, DisplayCardC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleDisplayCard))
                .playToClient(DisplayCardsS2CPayload.TYPE, DisplayCardsS2CPayload.CODEC)
                .playToClient(TokenUsageResponseS2CPayload.TYPE, TokenUsageResponseS2CPayload.CODEC);
    }

    /**
     * Resolve client handlers only on the physical client, keeping dedicated servers free of UI classes.
     */
    @EventBusSubscriber(Dist.CLIENT)
    public static final class ClientRegistration {
        @SubscribeEvent
        public static void register(RegisterClientPayloadHandlersEvent event) {
            ASSISTANT.registerClient(event, ClientNetworkHandler::handleAssistantResponse);
            event.register(DisplayCardsS2CPayload.TYPE, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleDisplayCards));
            event.register(TokenUsageResponseS2CPayload.TYPE, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleTokenUsageResponse));
        }
    }
}
