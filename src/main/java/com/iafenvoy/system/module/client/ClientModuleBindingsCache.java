package com.iafenvoy.system.module.client;

import com.iafenvoy.saturn.registry.cache.RegistryCache;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.registry.SystemRegistries;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Owns workspace declarations and local actions; one Saturn lifecycle also maintains the command cache.
 */
public final class ClientModuleBindingsCache extends RegistryCache<ClientModuleBindingsCache> {
    private List<Workspace> workspace = List.of();
    private Map<Identifier, BiConsumer<CardRef, Map<String, String>>> localActions = Map.of();
    private boolean initialized;
    private boolean building;

    @Override
    protected synchronized void build(RegistryAccess access) {
        // These modules belong to the frozen builtin registry, not the world's datapack registries.
        this.workspace = new ArrayList<>();
        this.localActions = new LinkedHashMap<>();
        this.building = true;
        ClientModuleCaches.COMMAND.begin();
        try {
            for (ClientModule module : SystemRegistries.CLIENT_MODULE) {
                Identifier id = Objects.requireNonNull(SystemRegistries.CLIENT_MODULE.getKey(module));
                module.register(new ClientModuleContext(this, id, module));
                ClientModuleCaches.COMMAND.game(module::clientCommands);
            }
            this.workspace = List.copyOf(this.workspace);
            this.localActions = Map.copyOf(this.localActions);
            ClientModuleCaches.COMMAND.finish();
            this.initialized = true;
        } catch (RuntimeException | Error error) {
            this.clear();
            throw error;
        } finally {
            this.building = false;
        }
    }

    @Override
    protected synchronized void clear() {
        this.initialized = false;
        this.building = false;
        this.workspace = List.of();
        this.localActions = Map.of();
        ClientModuleCaches.COMMAND.clear();
    }

    @Override
    public synchronized ClientModuleBindingsCache get() {
        if (this.building) throw new IllegalStateException("Client module registration is still in progress");
        // Local settings can request bindings before a world has supplied tags.
        if (!this.initialized) this.build(RegistryAccess.EMPTY);
        return this;
    }

    void localAction(Identifier id, BiConsumer<CardRef, Map<String, String>> handler) {
        if (!this.building) throw new IllegalStateException("Client module registration has finished");
        if (this.localActions.putIfAbsent(id, handler) != null)
            throw new IllegalArgumentException("Duplicate local card action " + id);
    }

    public void localAction(Identifier id, CardRef source, Map<String, String> arguments) {
        BiConsumer<CardRef, Map<String, String>> handler = this.localActions.get(id);
        if (handler != null) handler.accept(source, arguments);
    }

    void workspace(Workspace entry) {
        if (!this.building) throw new IllegalStateException("Client module registration has finished");
        for (Workspace previous : this.workspace)
            if (!entry.page().isBlank() && previous.page().equals(entry.page())
                    || !entry.navigation().isBlank() && previous.navigation().equals(entry.navigation()))
                throw new IllegalArgumentException("Duplicate workspace entry " + entry);
        this.workspace.add(entry);
    }

    public void bindWorkspace(AssistantScreen screen, Document document) {
        Element navigation = document.getElementById("sidebar_navigation");
        navigation.setInnerHTML("");
        for (WorkspaceGroup category : WorkspaceGroup.values()) {
            Element group = AssistantView.element(document, "div", "navigation_group", "");
            group.setAttribute("id", "nav_group_" + category.id());
            group.appendChild(AssistantView.element(document, "div", "section_code", AssistantView.tr(category.label())));
            for (Workspace entry : this.workspace) {
                if (entry.group() != category || entry.navigation().isBlank()) continue;
                Element button = AssistantView.element(document, "button", "nav", AssistantView.tr(entry.label()));
                button.setAttribute("id", entry.navigation());
                group.appendChild(button);
            }
            navigation.appendChild(group);
        }
        this.workspace.forEach(entry -> entry.bind().accept(new WorkspaceContext(screen, document, entry::available)));
    }

    public List<Workspace> workspace() {
        return this.workspace;
    }

    public record Workspace(ClientModuleContext context, String page, String navigation, String label,
                            WorkspaceGroup group, Consumer<WorkspaceContext> bind) {
        public boolean available() {
            return this.context.available();
        }
    }
}
