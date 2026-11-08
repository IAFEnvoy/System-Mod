package com.iafenvoy.system.network;

import com.iafenvoy.system.network.payload.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Wire registration only; processing and connection state belong to the side handlers.
 */
@EventBusSubscriber
public final class NetworkManager {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AssistantRequestC2SPayload.TYPE, AssistantRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleAssistantRequest))
                .playToServer(TokenUsageRequestC2SPayload.TYPE, TokenUsageRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleTokenUsageRequest))
                .playToServer(DisplayCardC2SPayload.TYPE, DisplayCardC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleDisplayCard));
        // Client handlers live in their own class so dedicated servers never load UI classes.
        if (FMLEnvironment.dist.isClient()) ClientRegistration.register(registrar);
    }

    /**
     * Resolve client handlers only on the physical client, keeping dedicated servers free of UI classes.
     */
    public static final class ClientRegistration {
        private ClientRegistration() {
        }

        public static void register(PayloadRegistrar registrar) {
            registrar.playToClient(AssistantResponseS2CPayload.TYPE, AssistantResponseS2CPayload.CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleAssistantResponse))
                    .playToClient(DisplayCardsS2CPayload.TYPE, DisplayCardsS2CPayload.CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleDisplayCards))
                    .playToClient(TokenUsageResponseS2CPayload.TYPE, TokenUsageResponseS2CPayload.CODEC, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleTokenUsageResponse));
        }
    }
}