package com.iafenvoy.system.module.client;

import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.registry.SystemClientModules;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns workspace declarations and local actions; client registry sync also maintains the command cache.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ClientModuleBindingsCache implements Supplier<ClientModuleBindingsCache> {
    private List<Workspace> workspace = List.of();
    private Map<Identifier, BiConsumer<CardRef, Map<String, String>>> localActions = Map.of();
    private RegistryAccess registries;
    private boolean building;

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent.ClientPacketReceived event) {
        // Client bindings need every connection's snapshot, including integrated-server packets.
        ClientModuleCaches.BINDINGS.rebuild(event.getRegistries());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientModuleCaches.BINDINGS.clear();
    }

    private synchronized void rebuild(RegistryAccess access) {
        // Several configured entries can share one UI; group by client type before declaring commands and pages.
        this.workspace = new ArrayList<>();
        this.localActions = new LinkedHashMap<>();
        this.building = true;
        ClientModuleCaches.COMMAND.begin();
        try {
            SystemClientModules.local().forEach((id, module) -> this.register(id, module, List.of()));
            Map<Identifier, List<Identifier>> definitions = new LinkedHashMap<>();
            SystemDataPackRegistries.modules(access).ifPresent(registry ->
                    SystemDataPackRegistries.moduleIds(access).forEach(id -> registry.getValue(id).client().ifPresent(type ->
                            definitions.computeIfAbsent(type, _ -> new ArrayList<>()).add(id))));
            definitions.forEach((type, ids) -> {
                if (SystemClientModules.local().containsKey(type)) return;
                ClientModule module = SystemClientModules.get(type);
                if (module != null) this.register(type, module, ids);
            });
            this.workspace = List.copyOf(this.workspace);
            this.localActions = Map.copyOf(this.localActions);
            ClientModuleCaches.COMMAND.finish();
            this.registries = access;
        } catch (RuntimeException | Error error) {
            this.clear();
            throw error;
        } finally {
            this.building = false;
        }
    }

    private void register(Identifier id, ClientModule module, List<Identifier> definitions) {
        module.register(new ClientModuleContext(this, id, module, definitions));
        ClientModuleCaches.COMMAND.game(module::clientCommands);
    }

    private synchronized void clear() {
        this.registries = null;
        this.building = false;
        this.workspace = List.of();
        this.localActions = Map.of();
        ClientModuleCaches.COMMAND.clear();
    }

    @Override
    public synchronized ClientModuleBindingsCache get() {
        if (this.building) throw new IllegalStateException("Client module registration is still in progress");
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        // A tag packet may arrive before the local player; keep its snapshot until a connection is available.
        if (connection != null) {
            if (this.registries != connection.registryAccess()) this.rebuild(connection.registryAccess());
        } else if (this.registries == null) this.rebuild(RegistryAccess.EMPTY);
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
        Element topbar = document.getElementById("topbar_navigation");
        navigation.setInnerHTML("");
        topbar.setInnerHTML("");
        for (Workspace entry : this.workspace) {
            if (entry.navigation().isBlank()) continue;
            Element button = AssistantView.element(document, "button", "nav", AssistantView.tr(entry.label()));
            button.setAttribute("id", entry.navigation());
            (entry.group() == WorkspaceGroup.SETTINGS ? topbar : navigation).appendChild(button);
        }
        this.workspace.forEach(entry -> entry.bind().accept(new WorkspaceContext(screen, document, entry)));
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
