package com.iafenvoy.system.module.client;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record ClientModuleContext(ClientModuleBindingsCache bindings, ResourceLocation id, ClientModule module,
                                  List<ResourceLocation> definitions) {
    public ClientModuleContext {
        definitions = List.copyOf(definitions);
    }

    public void command(Runnable action, String... aliases) {
        ClientModuleCaches.COMMAND.register(this::available, action, aliases);
    }

    public void command(ArgumentBuilder<AssistantCommandSource, ?> node) {
        ClientModuleCaches.COMMAND.register(this::available, node);
    }

    public void suggest(ArgumentBuilder<AssistantCommandSource, ?> node) {
        ClientModuleCaches.COMMAND.suggest(this::available, node);
    }

    public boolean available() {
        return this.definitions.isEmpty() || this.definitions.stream().anyMatch(AssistantClient.state()::hasModule);
    }

    public void workspace(String page, String navigation, Consumer<WorkspaceContext> bind) {
        this.workspace(page, navigation, WorkspaceGroup.MODULES, bind);
    }

    public void workspace(String page, String navigation, WorkspaceGroup group, Consumer<WorkspaceContext> bind) {
        this.workspace(page, navigation, "system.ui." + navigation, group, bind);
    }

    public void workspace(String page, String navigation, String label, WorkspaceGroup group, Consumer<WorkspaceContext> bind) {
        this.bindings.workspace(new ClientModuleBindingsCache.Workspace(this, page, navigation, label, group, bind));
    }

    public void localAction(ResourceLocation action, BiConsumer<CardRef, Map<String, String>> handler) {
        this.bindings.localAction(action, (source, arguments) -> {
            if (this.available()) handler.accept(source, arguments);
        });
    }
}
