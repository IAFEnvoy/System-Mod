package com.iafenvoy.system.network;

import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.network.payload.AssistantResponseS2CPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;

/**
 * Wire registration only; processing and connection state belong to the side handlers.
 *
 * <p>1.21.1 has no client-only payload registration event, so the response handlers are installed from a subscriber
 * that exists only on the physical client. A dedicated server therefore never links the client screen classes that
 * {@link ClientNetworkHandler} pulls in.
 */
public final class NetworkManager {
    /**
     * Shared payload declarations; both sides must agree on ids and codecs.
     */
    @EventBusSubscriber
    public static final class Common {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1")
                    .playToServer(AssistantRequestC2SPayload.TYPE, AssistantRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleAssistantRequest))
                    .playToServer(TokenUsageRequestC2SPayload.TYPE, TokenUsageRequestC2SPayload.CODEC, new MainThreadPayloadHandler<>(ServerNetworkHandler::handleTokenUsageRequest));
        }
    }

    /**
     * Client responses, registered only where the client classes exist.
     */
    @EventBusSubscriber(Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1")
                    .playToClient(AssistantResponseS2CPayload.TYPE, AssistantResponseS2CPayload.CODEC,
                            new MainThreadPayloadHandler<>(ClientNetworkHandler::handleAssistantResponse))
                    .playToClient(TokenUsageResponseS2CPayload.TYPE, TokenUsageResponseS2CPayload.CODEC,
                            new MainThreadPayloadHandler<>(ClientNetworkHandler::handleTokenUsageResponse));
        }
    }

    private NetworkManager() {
    }
}
