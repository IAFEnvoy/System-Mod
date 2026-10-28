package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.advancement.AdvancementSystemModule;
import com.iafenvoy.system.module.binding.BindingSystemModule;
import com.iafenvoy.system.module.core.CoreSystemModule;
import com.iafenvoy.system.module.inbox.InboxSystemModule;
import com.iafenvoy.system.module.item.ItemSystemModule;
import com.iafenvoy.system.module.lottery.LotterySystemModule;
import com.iafenvoy.system.module.player.PlayerSystemModule;
import com.iafenvoy.system.module.quest.QuestSystemModule;
import com.iafenvoy.system.module.search.SearchSystemModule;
import com.iafenvoy.system.module.shop.ShopSystemModule;
import com.iafenvoy.system.module.signin.SignInSystemModule;
import com.iafenvoy.system.module.usage.UsageSystemModule;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Builtin feature definitions are registered once; workflows and player runtimes are per server.
 */
@SuppressWarnings("unused")
public final class SystemServerModules {
    public static final DeferredRegister<ServerModule> REGISTRY = DeferredRegister.create(SystemResourceKeys.SERVER_MODULE, SystemMod.MOD_ID);

    public static final DeferredHolder<ServerModule, CoreSystemModule> CORE = REGISTRY.register("core", CoreSystemModule::new);
    public static final DeferredHolder<ServerModule, BindingSystemModule> BINDING = REGISTRY.register("binding", BindingSystemModule::new);
    public static final DeferredHolder<ServerModule, UsageSystemModule> USAGE = REGISTRY.register("usage", UsageSystemModule::new);
    public static final DeferredHolder<ServerModule, PlayerSystemModule> PLAYER = REGISTRY.register("player", PlayerSystemModule::new);
    public static final DeferredHolder<ServerModule, ItemSystemModule> ITEMS = REGISTRY.register("items", ItemSystemModule::new);
    public static final DeferredHolder<ServerModule, SignInSystemModule> SIGN_IN = REGISTRY.register("signin", SignInSystemModule::new);
    public static final DeferredHolder<ServerModule, InboxSystemModule> INBOX = REGISTRY.register("inbox", InboxSystemModule::new);
    public static final DeferredHolder<ServerModule, ShopSystemModule> SHOP = REGISTRY.register("shop", ShopSystemModule::new);
    public static final DeferredHolder<ServerModule, LotterySystemModule> LOTTERY = REGISTRY.register("lottery", LotterySystemModule::new);
    public static final DeferredHolder<ServerModule, AdvancementSystemModule> ADVANCEMENT = REGISTRY.register("advancement", AdvancementSystemModule::new);
    public static final DeferredHolder<ServerModule, QuestSystemModule> FTB_QUESTS = REGISTRY.register("ftb_quests", QuestSystemModule::new);
    public static final DeferredHolder<ServerModule, SearchSystemModule> SEARCH = REGISTRY.register("web_search", SearchSystemModule::new);
}
