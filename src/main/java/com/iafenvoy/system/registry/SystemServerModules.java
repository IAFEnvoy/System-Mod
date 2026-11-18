package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Builtin server module codecs are registered once; module instances are loaded from datapacks.
 */
@SuppressWarnings("unused")
public final class SystemServerModules {
    public static final DeferredRegister<MapCodec<? extends ServerModule>> REGISTRY = DeferredRegister.create(SystemResourceKeys.SERVER_MODULE, SystemMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<AchievementSystemModule>> ACHIEVEMENT = REGISTRY.register("achievement", () -> AchievementSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<AnchorSystemModule>> ANCHOR = REGISTRY.register("anchor", () -> AnchorSystemModule.CODEC);

    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<CoreSystemModule>> CORE = REGISTRY.register("core", () -> CoreSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<BindingSystemModule>> BINDING = REGISTRY.register("binding", () -> BindingSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<UsageSystemModule>> USAGE = REGISTRY.register("usage", () -> UsageSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<PlayerSystemModule>> PLAYER = REGISTRY.register("player", () -> PlayerSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ItemSystemModule>> ITEMS = REGISTRY.register("items", () -> ItemSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<EconomySystemModule>> ECONOMY = REGISTRY.register("economy", () -> EconomySystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<WorkshopSystemModule>> WORKSHOP = REGISTRY.register("workshop", () -> WorkshopSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<OnlineJobsSystemModule>> ONLINE_JOBS = REGISTRY.register("online_jobs", () -> OnlineJobsSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<SignInSystemModule>> SIGN_IN = REGISTRY.register("signin", () -> SignInSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<SpaceSystemModule>> SPACE = REGISTRY.register("inbox", () -> SpaceSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ShopSystemModule>> SHOP = REGISTRY.register("shop", () -> ShopSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ValueShopSystemModule>> VALUE_SHOP = REGISTRY.register("value_shop", () -> ValueShopSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ValueBuybackSystemModule>> VALUE_BUYBACK = REGISTRY.register("value_buyback", () -> ValueBuybackSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<LotterySystemModule>> LOTTERY = REGISTRY.register("lottery", () -> LotterySystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<AdvancementSystemModule>> ADVANCEMENT = REGISTRY.register("advancement", () -> AdvancementSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<QuestSystemModule>> FTB_QUESTS = REGISTRY.register("ftb_quests", () -> QuestSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<SearchSystemModule>> SEARCH = REGISTRY.register("web_search", () -> SearchSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<CollectionSystemModule>> COLLECTION = REGISTRY.register("collection", () -> CollectionSystemModule.CODEC);
}
