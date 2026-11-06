package com.iafenvoy.system.module.client;

public final class ClientModuleCaches {
    public static final ClientCommandCache COMMAND = new ClientCommandCache();
    public static final ClientModuleBindingsCache BINDINGS = new ClientModuleBindingsCache();
    public static final QuestPinCache QUEST_PINS = new QuestPinCache();

    private ClientModuleCaches() {
    }

    public static void init() {
    }
}
