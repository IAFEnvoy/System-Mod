package com.iafenvoy.system.module.client;

/**
 * Modules choose navigation placement; settings go in the header without category headings.
 */
public enum WorkspaceGroup {
    CHAT("chat"), MODULES("modules"), SETTINGS("settings");

    private final String id;

    WorkspaceGroup(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public String label() {
        return "system.ui.sidebar." + this.id;
    }
}
