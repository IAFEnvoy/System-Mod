package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.request.AgentRequest;
import com.iafenvoy.system.agent.response.AgentResponse;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.screen.card.CardNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

@SuppressWarnings("unused")
public final class SystemResourceKeys {
    // Builtin
    public static final ResourceKey<Registry<MapCodec<? extends ServerModule>>> SERVER_MODULE = create("server_module");
    public static final ResourceKey<Registry<MapCodec<? extends CardData>>> CARD_DATA_TYPE = create("card_data_type");
    public static final ResourceKey<Registry<MapCodec<? extends CardNode>>> CARD_NODE_TYPE = create("card_node_type");
    public static final ResourceKey<Registry<MapCodec<? extends AgentAction>>> ACTION_TYPE = create("action_type");
    public static final ResourceKey<Registry<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>>> AGENT_REQUEST_TYPE = create("agent_request_type");
    public static final ResourceKey<Registry<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>>> AGENT_RESPONSE_TYPE = create("agent_response_type");
    // Datapack
    public static final ResourceKey<Registry<CurrencyDefinition>> CURRENCY = create("currency");
    public static final ResourceKey<Registry<OnlineJobEntry>> ONLINE_JOB = create("online_job");
    public static final ResourceKey<Registry<SystemModule>> SYSTEM_MODULE = create("system_module");
    public static final ResourceKey<Registry<SystemDefinition>> SYSTEM = create("system");
    public static final ResourceKey<Registry<SignInRewardEntry>> SIGNIN_REWARD = create("signin_reward");
    public static final ResourceKey<Registry<ShopOfferEntry>> SHOP_OFFER = create("shop_offer");
    public static final ResourceKey<Registry<LotteryPool>> LOTTERY_POOL = create("lottery_pool");

    private static <T> ResourceKey<Registry<T>> create(String id) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, id));
    }
}
