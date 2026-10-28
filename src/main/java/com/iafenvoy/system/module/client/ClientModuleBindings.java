package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.registry.SystemRegistries;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Runtime presentation contributions are assembled once from the frozen client module registry. */
public final class ClientModuleBindings {
    private final ClientCommandRegistry commands = new ClientCommandRegistry();
    private final ClientCardBindings cards = new ClientCardBindings();
    private final List<Workspace> workspace = new ArrayList<>();
    private final Map<String, NotificationAction> notificationActions = new LinkedHashMap<>();
    private boolean frozen;

    private ClientModuleBindings() {
    }

    private static final class Holder {
        private static final ClientModuleBindings INSTANCE = create();
    }

    public static ClientModuleBindings get() {
        return Holder.INSTANCE;
    }

    private static ClientModuleBindings create() {
        ClientModuleBindings bindings = new ClientModuleBindings();
        for (ClientModule module : SystemRegistries.CLIENT_MODULE)
            module.register(new ClientModuleContext(bindings, module.id(), module.serverRequired()));
        bindings.freeze();
        return bindings;
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client module registration has finished");
    }

    private void freeze() {
        this.frozen = true;
        this.commands.freeze();
        this.cards.freeze();
    }

    public void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        SystemRegistries.CLIENT_MODULE.forEach(module -> module.commands(root));
    }

    public ClientCommandRegistry commands() {
        return this.commands;
    }

    public ClientCardBindings cards() {
        return this.cards;
    }

    void workspace(Workspace entry) {
        this.checkMutable();
        for (Workspace previous : this.workspace)
            if (!entry.page().isBlank() && previous.page().equals(entry.page())
                    || !entry.navigation().isBlank() && previous.navigation().equals(entry.navigation()))
                throw new IllegalArgumentException("Duplicate workspace entry " + entry);
        this.workspace.add(entry);
    }

    public record NotificationAction(boolean details, boolean keepOpen, boolean openWorkspace) {
        public static final NotificationAction DEFAULT = new NotificationAction(false, false, false);
        public static final NotificationAction DETAILS = new NotificationAction(true, false, false);
        public static final NotificationAction DETAILS_IN_WORKSPACE = new NotificationAction(true, false, true);
        public static final NotificationAction KEEP_OPEN = new NotificationAction(false, true, false);
    }

    public void notificationAction(NotificationAction action, String... labels) {
        this.checkMutable();
        for (String label : labels)
            if (this.notificationActions.putIfAbsent(label, action) != null)
                throw new IllegalArgumentException("Duplicate notification action " + label);
    }

    public NotificationAction notificationAction(AgentResult.Action action) {
        return this.notificationActions.getOrDefault(action.labelKey(), NotificationAction.DEFAULT);
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
        return List.copyOf(this.workspace);
    }

    public record Workspace(Identifier owner, boolean serverRequired, String page, String navigation, String label, WorkspaceGroup group,
                            Consumer<WorkspaceContext> bind) {
        public boolean available() {
            return !this.serverRequired || AssistantClient.state().hasModule(this.owner);
        }
    }
}
