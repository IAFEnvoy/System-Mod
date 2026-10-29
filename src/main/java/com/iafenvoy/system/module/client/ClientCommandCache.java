package com.iafenvoy.system.module.client;

import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.util.CommandTrees;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Owns command registration and compiled trees; bindings rebuild and clear it in the same lifecycle.
 */
public final class ClientCommandCache {
    private final List<ArgumentBuilder<AssistantCommandSource, ?>> nodes = new ArrayList<>();
    private final List<ArgumentBuilder<AssistantCommandSource, ?>> suggestions = new ArrayList<>();
    private LiteralArgumentBuilder<CommandSourceStack> gameBuilder;
    private CommandDispatcher<AssistantCommandSource> dispatcher;
    private CommandDispatcher<AssistantCommandSource> completion;
    private LiteralCommandNode<CommandSourceStack> gameRoot;
    private boolean building;
    private long revision;

    void begin() {
        this.clear();
        this.gameBuilder = Commands.literal("system");
        this.building = true;
    }

    void clear() {
        this.building = false;
        this.nodes.clear();
        this.suggestions.clear();
        this.gameBuilder = null;
        this.dispatcher = null;
        this.completion = null;
        this.gameRoot = null;
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
        this.gameRoot = this.gameBuilder.build();
        this.nodes.clear();
        this.suggestions.clear();
        this.gameBuilder = null;
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
                    .executes(unused -> {
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

    void game(Consumer<LiteralArgumentBuilder<CommandSourceStack>> registration) {
        this.requireBuilding();
        registration.accept(this.gameBuilder);
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

    public LiteralCommandNode<CommandSourceStack> gameRoot() {
        return this.gameRoot;
    }

    public long revision() {
        return this.revision;
    }
}
