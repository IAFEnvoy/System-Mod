package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Function;

public record ClientModuleContext(ClientModuleRegistry registry, Identifier id, boolean serverRequired) {
    public void command(Runnable action, String... aliases) {
        this.registry.commands().register(action, aliases);
    }

    public void pattern(Function<String, Boolean> handler, String... suggestions) {
        this.registry.commands().pattern(handler, suggestions);
    }

    public void workspace(String page, String navigation, Consumer<WorkspaceContext> bind) {
        this.registry.workspace(new ClientModuleRegistry.Workspace(this.id, this.serverRequired, page, navigation, bind));
    }

    public ClientCardRegistry cards() {
        return this.registry.cards();
    }

    public void speech(String key, Function<AgentResult, String> handler) {
        this.registry.speech(key, handler);
    }
}
