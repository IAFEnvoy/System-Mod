package com.iafenvoy.system.registry;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.credential.Credential;
import com.iafenvoy.assistant.registry.AssistantRegistries;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.screen.card.CardNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Code-defined catalogs join the builtin root registry and freeze during mod loading.
 */
@SuppressWarnings("unused")
@EventBusSubscriber
public final class SystemRegistries {
    public static final Registry<SpaceContentType<?>> SPACE_CONTENT_TYPE = new RegistryBuilder<>(SystemResourceKeys.SPACE_CONTENT_TYPE).create();
    public static final Registry<MapCodec<? extends ServerModule>> SERVER_MODULE = new RegistryBuilder<>(SystemResourceKeys.SERVER_MODULE).create();
    public static final Registry<MapCodec<? extends CardData>> CARD_DATA_TYPE = new RegistryBuilder<>(SystemResourceKeys.CARD_DATA_TYPE).create();
    public static final Registry<MapCodec<? extends CardNode>> CARD_NODE_TYPE = new RegistryBuilder<>(SystemResourceKeys.CARD_NODE_TYPE).create();
    public static final Registry<MapCodec<? extends AgentAction>> ACTION_TYPE = AssistantRegistries.ACTION_TYPE;
    public static final Registry<MapCodec<? extends Credential>> CREDENTIAL_TYPE = AssistantRegistries.CREDENTIAL_TYPE;

    @SubscribeEvent
    public static void register(NewRegistryEvent event) {
        event.register(SPACE_CONTENT_TYPE);
        event.register(SERVER_MODULE);
        event.register(CARD_DATA_TYPE);
        event.register(CARD_NODE_TYPE);
    }
}
