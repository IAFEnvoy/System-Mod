package com.iafenvoy.system.registry;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.screen.card.CardNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Code-defined catalogs join the builtin root registry and freeze during mod loading. */
@SuppressWarnings("unused")
@EventBusSubscriber
public final class SystemRegistries {
    public static final Registry<ServerModule> SERVER_MODULE = new RegistryBuilder<>(SystemResourceKeys.SERVER_MODULE).create();
    public static final Registry<MapCodec<? extends CardData>> CARD_DATA_TYPE = new RegistryBuilder<>(SystemResourceKeys.CARD_DATA_TYPE).create();
    public static final Registry<TypedPayload.Type<?>> ACTION_TYPE = new RegistryBuilder<>(SystemResourceKeys.ACTION_TYPE).create();
    public static final Registry<MapCodec<? extends AgentRequest>> AGENT_REQUEST_TYPE = new RegistryBuilder<>(SystemResourceKeys.AGENT_REQUEST_TYPE).create();
    public static final Registry<MapCodec<? extends AgentResponse>> AGENT_RESPONSE_TYPE = new RegistryBuilder<>(SystemResourceKeys.AGENT_RESPONSE_TYPE).create();
    // Generic value types do not initialize client implementations; only the client entrypoint registers them.
    public static final Registry<ClientModule> CLIENT_MODULE = new RegistryBuilder<>(SystemResourceKeys.CLIENT_MODULE).create();
    public static final Registry<MapCodec<? extends CardNode>> CARD_NODE_TYPE = new RegistryBuilder<>(SystemResourceKeys.CARD_NODE_TYPE).create();

    private SystemRegistries() {
    }

    @SubscribeEvent
    public static void register(NewRegistryEvent event) {
        event.register(SERVER_MODULE);
        event.register(CARD_DATA_TYPE);
        event.register(ACTION_TYPE);
        event.register(AGENT_REQUEST_TYPE);
        event.register(AGENT_RESPONSE_TYPE);
        event.register(CLIENT_MODULE);
        event.register(CARD_NODE_TYPE);
    }
}
