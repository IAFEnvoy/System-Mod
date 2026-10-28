package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.binding.BindingClientModule;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.conversation.ConversationClientModule;
import com.iafenvoy.system.module.core.CoreClientModule;
import com.iafenvoy.system.module.inbox.InboxClientModule;
import com.iafenvoy.system.module.item.ItemClientModule;
import com.iafenvoy.system.module.lottery.LotteryClientModule;
import com.iafenvoy.system.module.player.PlayerClientModule;
import com.iafenvoy.system.module.quest.QuestClientModule;
import com.iafenvoy.system.module.search.SearchClientModule;
import com.iafenvoy.system.module.settings.SettingsClientModule;
import com.iafenvoy.system.module.shop.ShopClientModule;
import com.iafenvoy.system.module.signin.SignInClientModule;
import com.iafenvoy.system.module.speech.SpeechClientModule;
import com.iafenvoy.system.module.usage.UsageClientModule;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Loaded only by the client entrypoint; no server or player state belongs to these definitions.
 */
@SuppressWarnings("unused")
public final class SystemClientModules {
    public static final DeferredRegister<ClientModule> REGISTRY = DeferredRegister.create(SystemResourceKeys.CLIENT_MODULE, SystemMod.MOD_ID);

    public static final DeferredHolder<ClientModule, CoreClientModule> CORE = REGISTRY.register("core", CoreClientModule::new);
    public static final DeferredHolder<ClientModule, BindingClientModule> BINDING = REGISTRY.register("binding", BindingClientModule::new);
    public static final DeferredHolder<ClientModule, ConversationClientModule> CONVERSATION = REGISTRY.register("conversation", ConversationClientModule::new);
    public static final DeferredHolder<ClientModule, SettingsClientModule> SETTINGS = REGISTRY.register("settings", SettingsClientModule::new);
    public static final DeferredHolder<ClientModule, UsageClientModule> USAGE = REGISTRY.register("usage", UsageClientModule::new);
    public static final DeferredHolder<ClientModule, SpeechClientModule> SPEECH = REGISTRY.register("speech", SpeechClientModule::new);
    public static final DeferredHolder<ClientModule, PlayerClientModule> PLAYER = REGISTRY.register("player", PlayerClientModule::new);
    public static final DeferredHolder<ClientModule, ItemClientModule> ITEMS = REGISTRY.register("items", ItemClientModule::new);
    public static final DeferredHolder<ClientModule, SignInClientModule> SIGN_IN = REGISTRY.register("signin", SignInClientModule::new);
    public static final DeferredHolder<ClientModule, InboxClientModule> INBOX = REGISTRY.register("inbox", InboxClientModule::new);
    public static final DeferredHolder<ClientModule, ShopClientModule> SHOP = REGISTRY.register("shop", ShopClientModule::new);
    public static final DeferredHolder<ClientModule, LotteryClientModule> LOTTERY = REGISTRY.register("lottery", LotteryClientModule::new);
    public static final DeferredHolder<ClientModule, QuestClientModule> FTB_QUESTS = REGISTRY.register("ftb_quests", QuestClientModule::new);
    public static final DeferredHolder<ClientModule, SearchClientModule> SEARCH = REGISTRY.register("search", SearchClientModule::new);
}
