package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.builtin.BindingSystemModule;
import com.iafenvoy.system.module.builtin.ConversationClientModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import com.iafenvoy.system.module.builtin.ItemSystemModule;
import com.iafenvoy.system.module.builtin.LotterySystemModule;
import com.iafenvoy.system.module.builtin.PlayerSystemModule;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.module.builtin.SearchSystemModule;
import com.iafenvoy.system.module.builtin.SettingsClientModule;
import com.iafenvoy.system.module.builtin.ShopSystemModule;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import com.iafenvoy.system.module.builtin.SpeechClientModule;
import com.iafenvoy.system.module.builtin.UsageSystemModule;
import com.iafenvoy.system.module.client.ClientModule;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The client catalog reuses shared feature classes; their UI hooks are invoked only on the client.
 */
@SuppressWarnings("unused")
public final class SystemClientModules {
    public static final DeferredRegister<ClientModule> REGISTRY = DeferredRegister.create(SystemResourceKeys.CLIENT_MODULE, SystemMod.MOD_ID);

    public static final DeferredHolder<ClientModule, CoreSystemModule> CORE = REGISTRY.register("core", CoreSystemModule::new);
    public static final DeferredHolder<ClientModule, BindingSystemModule> BINDING = REGISTRY.register("binding", BindingSystemModule::new);
    public static final DeferredHolder<ClientModule, ConversationClientModule> CONVERSATION = REGISTRY.register("conversation", ConversationClientModule::new);
    public static final DeferredHolder<ClientModule, SettingsClientModule> SETTINGS = REGISTRY.register("settings", SettingsClientModule::new);
    public static final DeferredHolder<ClientModule, UsageSystemModule> USAGE = REGISTRY.register("usage", UsageSystemModule::new);
    public static final DeferredHolder<ClientModule, SpeechClientModule> SPEECH = REGISTRY.register("speech", SpeechClientModule::new);
    public static final DeferredHolder<ClientModule, PlayerSystemModule> PLAYER = REGISTRY.register("player", PlayerSystemModule::new);
    public static final DeferredHolder<ClientModule, ItemSystemModule> ITEMS = REGISTRY.register("items", ItemSystemModule::new);
    public static final DeferredHolder<ClientModule, SignInSystemModule> SIGN_IN = REGISTRY.register("signin", SignInSystemModule::new);
    public static final DeferredHolder<ClientModule, InboxSystemModule> INBOX = REGISTRY.register("inbox", InboxSystemModule::new);
    public static final DeferredHolder<ClientModule, ShopSystemModule> SHOP = REGISTRY.register("shop", ShopSystemModule::new);
    public static final DeferredHolder<ClientModule, LotterySystemModule> LOTTERY = REGISTRY.register("lottery", LotterySystemModule::new);
    public static final DeferredHolder<ClientModule, QuestSystemModule> FTB_QUESTS = REGISTRY.register("ftb_quests", QuestSystemModule::new);
    public static final DeferredHolder<ClientModule, SearchSystemModule> SEARCH = REGISTRY.register("web_search", SearchSystemModule::new);
}
