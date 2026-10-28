package com.iafenvoy.system.module.client;

/** The workspace owns category order; modules choose where their navigation entry belongs. */
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
