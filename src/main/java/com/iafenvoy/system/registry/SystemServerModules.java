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

    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<CoreSystemModule>> CORE = REGISTRY.register("core", () -> CoreSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<BindingSystemModule>> BINDING = REGISTRY.register("binding", () -> BindingSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<UsageSystemModule>> USAGE = REGISTRY.register("usage", () -> UsageSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<PlayerSystemModule>> PLAYER = REGISTRY.register("player", () -> PlayerSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ItemSystemModule>> ITEMS = REGISTRY.register("items", () -> ItemSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<SignInSystemModule>> SIGN_IN = REGISTRY.register("signin", () -> SignInSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<InboxSystemModule>> INBOX = REGISTRY.register("inbox", () -> InboxSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<ShopSystemModule>> SHOP = REGISTRY.register("shop", () -> ShopSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<LotterySystemModule>> LOTTERY = REGISTRY.register("lottery", () -> LotterySystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<AdvancementSystemModule>> ADVANCEMENT = REGISTRY.register("advancement", () -> AdvancementSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<QuestSystemModule>> FTB_QUESTS = REGISTRY.register("ftb_quests", () -> QuestSystemModule.CODEC);
    public static final DeferredHolder<MapCodec<? extends ServerModule>, MapCodec<SearchSystemModule>> SEARCH = REGISTRY.register("web_search", () -> SearchSystemModule.CODEC);
}
