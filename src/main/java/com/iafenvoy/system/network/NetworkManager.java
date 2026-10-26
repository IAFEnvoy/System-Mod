package com.iafenvoy.system.network;

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
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3")
                .playToServer(AssistantRequestC2SPayload.TYPE, AssistantRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleAssistantRequest))
                .playToServer(TokenUsageRequestC2SPayload.TYPE, TokenUsageRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleTokenUsageRequest))
                .playToServer(SpeechUsageReportC2SPayload.TYPE, SpeechUsageReportC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleSpeechUsageReport))
                .playToServer(RecognitionUsageReportC2SPayload.TYPE, RecognitionUsageReportC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleRecognitionUsageReport))
                .playToClient(AssistantResponseS2CPayload.TYPE, AssistantResponseS2CPayload.CODEC)
                .playToClient(TokenUsageResponseS2CPayload.TYPE, TokenUsageResponseS2CPayload.CODEC);
    }

    /**
     * Resolve client handlers only on the physical client, keeping dedicated servers free of UI classes.
     */
    @EventBusSubscriber(Dist.CLIENT)
    public static final class ClientRegistration {
        @SubscribeEvent
        public static void register(RegisterClientPayloadHandlersEvent event) {
            event.register(AssistantResponseS2CPayload.TYPE, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleAssistantResponse));
            event.register(TokenUsageResponseS2CPayload.TYPE, new MainThreadPayloadHandler<>(ClientNetworkHandler::handleTokenUsageResponse));
        }
    }
}
