package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.request.CommandSuggestionsRequest;
import com.iafenvoy.system.agent.response.CommandSuggestionsResponse;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.data.CommandSuggestion;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.util.CommandTrees;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Owns command registration and compiled trees; bindings rebuild and clear it in the same lifecycle.
 */
public final class ClientCommandCache {
    private final List<ArgumentBuilder<AssistantCommandSource, ?>> nodes = new ArrayList<>();
    private final List<ArgumentBuilder<AssistantCommandSource, ?>> suggestions = new ArrayList<>();
    private CommandDispatcher<AssistantCommandSource> dispatcher;
    private CommandDispatcher<AssistantCommandSource> completion;
    private Pending pending;
    private boolean building;
    private long revision;

    private record Pending(String id, String input, CompletableFuture<List<CommandSuggestion>> result) {
    }

    void begin() {
        this.clear();
        this.building = true;
    }

    void clear() {
        this.building = false;
        this.nodes.clear();
        this.suggestions.clear();
        this.dispatcher = null;
        this.completion = null;
        this.cancelSuggestions();
        this.revision++;
    }

    void finish() {
        this.requireBuilding();
        this.dispatcher = new CommandDispatcher<>();
        this.completion = new CommandDispatcher<>();
        for (ArgumentBuilder<AssistantCommandSource, ?> node : this.nodes) {
            this.dispatcher.getRoot().addChild(CommandTrees.copy(node.build()));
            this.completion.getRoot().addChild(CommandTrees.copy(node.build()));
        }
        this.suggestions.forEach(node -> this.completion.getRoot().addChild(CommandTrees.copy(node.build())));
        this.nodes.clear();
        this.suggestions.clear();
        this.building = false;
    }

    public ClientCommandCache get() {
        ClientModuleCaches.BINDINGS.get();
        return this;
    }

    void register(BooleanSupplier available, Runnable action, String... aliases) {
        for (String alias : aliases) {
            String[] parts = alias.split(" ");
            LiteralArgumentBuilder<AssistantCommandSource> leaf = LiteralArgumentBuilder.<AssistantCommandSource>literal(parts[parts.length - 1])
                    .executes(_ -> {
                        action.run();
                        return 1;
                    });
            for (int i = parts.length - 2; i >= 0; i--)
                leaf = LiteralArgumentBuilder.<AssistantCommandSource>literal(parts[i]).then(leaf);
            this.register(available, leaf);
        }
    }

    void register(BooleanSupplier available, ArgumentBuilder<AssistantCommandSource, ?> node) {
        this.requireBuilding();
        Predicate<AssistantCommandSource> requirement = node.getRequirement();
        this.nodes.add(node.requires(source -> available.getAsBoolean() && requirement.test(source)));
    }

    void suggest(BooleanSupplier available, ArgumentBuilder<AssistantCommandSource, ?> node) {
        this.requireBuilding();
        Predicate<AssistantCommandSource> requirement = node.getRequirement();
        this.suggestions.add(node.requires(source -> available.getAsBoolean() && requirement.test(source)));
    }

    private void requireBuilding() {
        if (!this.building) throw new IllegalStateException("Client command registration has finished");
    }

    public CommandDispatcher<AssistantCommandSource> dispatcher() {
        return this.dispatcher;
    }

    public CommandDispatcher<AssistantCommandSource> suggestions(AssistantCommandSource source) {
        // Brigadier suggestions do not check requires; filter permissions for the current request.
        return CommandTrees.available(this.completion, source);
    }

    public CompletableFuture<List<CommandSuggestion>> remoteSuggestions(String input) {
        this.cancelSuggestions();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || input.length() > 2000 || AssistantClient.state().commands().isEmpty())
            return CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<CommandSuggestion>> result = new CompletableFuture<>();
        this.pending = new Pending(UUID.randomUUID().toString(), input, result);
        connection.send(new AssistantRequestC2SPayload(this.pending.id(), new CommandSuggestionsRequest(input)));
        return result.completeOnTimeout(List.of(), 3, TimeUnit.SECONDS);
    }

    public void receive(CommandSuggestionsResponse response) {
        Pending pending = this.pending;
        if (pending == null || !pending.id().equals(response.requestId()) || !pending.input().equals(response.input()))
            return;
        this.pending = null;
        pending.result().complete(response.suggestions());
    }

    private void cancelSuggestions() {
        Pending pending = this.pending;
        this.pending = null;
        // Binding rebuilds must not synchronously re-enter UI completion callbacks.
        if (pending != null) pending.result().cancel(false);
    }

    public long revision() {
        return this.revision;
    }
}
