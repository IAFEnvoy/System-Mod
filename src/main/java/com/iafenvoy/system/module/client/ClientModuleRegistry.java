package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.speech.tts.SpeechText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sighs.apricityui.init.Document;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ClientModuleRegistry {
    private final Map<Identifier, ClientModule> modules = new LinkedHashMap<>();
    private final ClientCommandRegistry commands = new ClientCommandRegistry();
    private final ClientCardRegistry cards = new ClientCardRegistry();
    private final List<Workspace> workspace = new ArrayList<>();
    private final Map<String, Function<AgentResult, String>> speech = new LinkedHashMap<>();
    private final Map<String, NotificationAction> notificationActions = new LinkedHashMap<>();
    private boolean frozen;

    public void register(ClientModule module) {
        this.checkMutable();
        if (this.modules.putIfAbsent(module.id(), module) != null)
            throw new IllegalArgumentException("Duplicate client module " + module.id());
        module.register(new ClientModuleContext(this, module.id(), module.serverRequired()));
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client module registration has finished");
    }

    void freeze() {
        this.frozen = true;
        this.commands.freeze();
        this.cards.freeze();
    }

    public void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        this.modules.values().forEach(module -> module.commands(root));
    }

    public ClientCommandRegistry commands() {
        return this.commands;
    }

    public ClientCardRegistry cards() {
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

    public void speech(String key, Function<AgentResult, String> handler) {
        this.checkMutable();
        if (this.speech.putIfAbsent(key, handler) != null)
            throw new IllegalArgumentException("Duplicate speech result " + key);
    }

    public String speech(AgentResult result) {
        return this.speech.getOrDefault(result.messageKey(), SpeechText::primary).apply(result);
    }

    public record NotificationAction(boolean details, boolean keepOpen, boolean openWorkspace) {
        public static final NotificationAction DEFAULT = new NotificationAction(false, false, false);
    }

    public void notificationAction(String label, NotificationAction action) {
        this.checkMutable();
        if (this.notificationActions.putIfAbsent(label, action) != null)
            throw new IllegalArgumentException("Duplicate notification action " + label);
    }

    public NotificationAction notificationAction(AgentResult.Action action) {
        return this.notificationActions.getOrDefault(action.labelKey(), NotificationAction.DEFAULT);
    }

    public void bindWorkspace(AssistantScreen screen, Document document) {
        WorkspaceContext context = new WorkspaceContext(screen, document);
        this.workspace.forEach(entry -> entry.bind().accept(context));
    }

    public List<Workspace> workspace() {
        return List.copyOf(this.workspace);
    }

    public record Workspace(Identifier owner, boolean serverRequired, String page, String navigation,
                            Consumer<WorkspaceContext> bind) {
        public boolean available() {
            return !this.serverRequired || AssistantClient.state().hasModule(this.owner);
        }
    }
}
