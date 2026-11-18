package com.iafenvoy.system.module.client;

public final class ClientModuleCaches {
    public static final ClientCommandCache COMMAND = new ClientCommandCache();
    public static final ClientModuleBindingsCache BINDINGS = new ClientModuleBindingsCache();
    public static final DisplayCardCache DISPLAY_CARDS = new DisplayCardCache();
    public static final SpaceSelectionCache SPACE = new SpaceSelectionCache();
    public static final ClientItemSearchCache ITEM_SEARCH = new ClientItemSearchCache();

    private ClientModuleCaches() {
    }

    public static void init() {
    }
}
