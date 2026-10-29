package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.SystemModule;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.screen.card.CardNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

@SuppressWarnings("unused")
public final class SystemResourceKeys {
    public static final ResourceKey<Registry<MapCodec<? extends ServerModule>>> SERVER_MODULE = create("server_module");
    public static final ResourceKey<Registry<MapCodec<? extends CardData>>> CARD_DATA_TYPE = create("card_data_type");
    public static final ResourceKey<Registry<MapCodec<? extends AgentAction>>> ACTION_TYPE = create("action_type");
    public static final ResourceKey<Registry<MapCodec<? extends AgentRequest>>> AGENT_REQUEST_TYPE = create("agent_request_type");
    public static final ResourceKey<Registry<MapCodec<? extends AgentResponse>>> AGENT_RESPONSE_TYPE = create("agent_response_type");
    public static final ResourceKey<Registry<MapCodec<? extends CardNode>>> CARD_NODE_TYPE = create("card_node_type");
    public static final ResourceKey<Registry<SystemModule>> SYSTEM_MODULE = create("system_module");
    public static final ResourceKey<Registry<SystemDefinition>> SYSTEM = create("system");

    private static <T> ResourceKey<Registry<T>> create(String id) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, id));
    }
}
