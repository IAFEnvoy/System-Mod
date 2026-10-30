package com.iafenvoy.system.registry;

import com.iafenvoy.system.module.builtin.*;
import com.iafenvoy.system.module.client.ClientModule;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client implementations are local objects; synchronized datapacks refer to them only by ID.
 */
@SuppressWarnings("unused")
public final class SystemClientModules {
    private static final Map<ResourceLocation, ClientModule> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ClientModule> LOCAL = new LinkedHashMap<>();

    public static final CoreSystemClientModule CORE = local("core", new CoreSystemClientModule());
    public static final BindingSystemClientModule BINDING = local("binding", new BindingSystemClientModule());
    public static final UsageSystemClientModule USAGE = local("usage", new UsageSystemClientModule());
    public static final ConversationClientModule CONVERSATION = local("conversation", new ConversationClientModule());
    public static final SettingsClientModule SETTINGS = local("settings", new SettingsClientModule());
    public static final SpeechClientModule SPEECH = local("speech", new SpeechClientModule());
    public static final PlayerSystemClientModule PLAYER = register("player", new PlayerSystemClientModule());
    public static final ItemSystemClientModule ITEMS = register("items", new ItemSystemClientModule());
    public static final SignInSystemClientModule SIGN_IN = register("signin", new SignInSystemClientModule());
    public static final InboxSystemClientModule INBOX = register("inbox", new InboxSystemClientModule());
    public static final ShopSystemClientModule SHOP = register("shop", new ShopSystemClientModule());
    public static final LotterySystemClientModule LOTTERY = register("lottery", new LotterySystemClientModule());
    public static final QuestSystemClientModule FTB_QUESTS = register("ftb_quests", new QuestSystemClientModule());
    public static final SearchSystemClientModule SEARCH = register("web_search", new SearchSystemClientModule());

    private SystemClientModules() {
    }

    public static void init() {
        // The client entrypoint initializes this class before the binding cache consumes its definitions.
    }

    private static <T extends ClientModule> T register(String id, T module) {
        return register(ResourceLocation.fromNamespaceAndPath("system", id), module);
    }

    public static <T extends ClientModule> T register(ResourceLocation id, T module) {
        if (TYPES.putIfAbsent(id, module) != null)
            throw new IllegalArgumentException("Duplicate client module type " + id);
        return module;
    }

    private static <T extends ClientModule> T local(String id, T module) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("system", id);
        register(key, module);
        LOCAL.put(key, module);
        return module;
    }

    public static ClientModule get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static Map<ResourceLocation, ClientModule> local() {
        return Collections.unmodifiableMap(LOCAL);
    }
}
