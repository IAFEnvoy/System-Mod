package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Function;

public record ClientModuleContext(ClientModuleBindings bindings, Identifier id, boolean serverRequired) {
    public void command(Runnable action, String... aliases) {
        this.bindings.commands().register(this::available, action, aliases);
    }

    public void pattern(Function<String, Boolean> handler, String... suggestions) {
        this.bindings.commands().pattern(this::available, handler, suggestions);
    }

    public boolean available() {
        return !this.serverRequired || AssistantClient.state().hasModule(this.id);
    }

    public void workspace(String page, String navigation, Consumer<WorkspaceContext> bind) {
        this.workspace(page, navigation, WorkspaceGroup.MODULES, bind);
    }

    public void workspace(String page, String navigation, WorkspaceGroup group, Consumer<WorkspaceContext> bind) {
        this.workspace(page, navigation, "system.ui." + navigation, group, bind);
    }

    public void workspace(String page, String navigation, String label, WorkspaceGroup group, Consumer<WorkspaceContext> bind) {
        this.bindings.workspace(new ClientModuleBindings.Workspace(this.id, this.serverRequired, page, navigation, label, group, bind));
    }

    public ClientCardRegistry cards() {
        return this.bindings.cards();
    }

    public void speech(String key, Function<AgentResult, String> handler) {
        this.bindings.speech(key, handler);
    }
}
