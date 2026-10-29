package com.iafenvoy.system.agent;

import com.iafenvoy.system.command.AssistantCommandInput;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.CommandTrees;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One per-engine Brigadier tree serves parsing, usage and completion; sources hold per-request authority.
 */
public final class CommandRegistry {
    private final CommandDispatcher<AssistantCommandSource> dispatcher = new CommandDispatcher<>();
    private final Map<String, Identifier> owners = new LinkedHashMap<>();
    private AgentAction root;

    <I extends AgentAction> void validate(List<AgentCommand<I>> commands) {
        Set<String> names = new HashSet<>(this.owners.keySet());
        for (AgentCommand<I> command : commands)
            for (String name : command.names())
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate command alias " + name);
    }

    <I extends AgentAction> void register(Identifier id, List<AgentCommand<I>> commands) {
        for (AgentCommand<I> command : commands)
            for (String name : command.names()) {
                LiteralArgumentBuilder<AssistantCommandSource> node = command.node().apply(name);
                Predicate<AssistantCommandSource> requirement = node.getRequirement();
                this.dispatcher.register(node.requires(source -> source.available(id) && requirement.test(source)));
                this.owners.put(name, id);
            }
    }

    void root(AgentAction input) {
        if (this.root != null) throw new IllegalArgumentException("Duplicate root command");
        this.root = input;
    }

    public List<String> suggestions(Predicate<Identifier> available) {
        AssistantCommandSource source = new AssistantCommandSource("", available);
        return this.dispatcher.getRoot().getChildren().stream().filter(node -> node.canUse(source))
                .map(node -> node.getName() + (node.getChildren().isEmpty() ? "" : " ")).toList();
    }

    public CompletableFuture<Suggestions> suggest(String input, Predicate<Identifier> available) {
        AssistantCommandInput command = AssistantCommandInput.of(input);
        AssistantCommandSource source = new AssistantCommandSource(input, available);
        CommandDispatcher<AssistantCommandSource> filtered = CommandTrees.available(this.dispatcher, source);
        return filtered.getCompletionSuggestions(filtered.parse(command.reader(filtered.getRoot()), source));
    }

    public List<String> usages(Predicate<Identifier> available) {
        return Arrays.stream(this.dispatcher.getAllUsage(this.dispatcher.getRoot(), new AssistantCommandSource("", available), true))
                .map(usage -> "/" + usage).toList();
    }

    /**
     * Share standard argument nodes with /system; execution still enters the normal Agent pipeline.
     */
    public <S> void attach(CommandNode<S> root, Function<S, Predicate<Identifier>> available, Command<S> execute) {
        for (CommandNode<AssistantCommandSource> node : this.dispatcher.getRoot().getChildren())
            if (root.getChild(node.getName()) == null) root.addChild(this.copy(node, available, execute));
    }

    private <S> CommandNode<S> copy(CommandNode<AssistantCommandSource> node,
                                    Function<S, Predicate<Identifier>> available, Command<S> execute) {
        ArgumentBuilder<S, ?> builder;
        if (node instanceof ArgumentCommandNode<AssistantCommandSource, ?> argument) {
            RequiredArgumentBuilder<S, Object> argumentBuilder = RequiredArgumentBuilder.argument(node.getName(), cast(argument.getType()));
            if (argument.getCustomSuggestions() != null)
                argumentBuilder.suggests((context, _) -> this.suggest("/" + context.getInput(), available.apply(context.getSource()))
                        .thenApply(suggestions -> Suggestions.create(context.getInput(), suggestions.getList().stream()
                                .map(suggestion -> new Suggestion(StringRange.between(suggestion.getRange().getStart() - 1,
                                        suggestion.getRange().getEnd() - 1), suggestion.getText(), suggestion.getTooltip())).toList())));
            builder = argumentBuilder;
        } else builder = LiteralArgumentBuilder.literal(node.getName());
        builder.requires(source -> node.canUse(new AssistantCommandSource("", available.apply(source))));
        if (node.getCommand() != null) builder.executes(execute);
        for (CommandNode<AssistantCommandSource> child : node.getChildren())
            builder.then(this.copy(child, available, execute));
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentType<Object> cast(ArgumentType<?> type) {
        return (ArgumentType<Object>) type;
    }

    public AgentAction parse(String text, Predicate<Identifier> available) {
        AssistantCommandInput command = AssistantCommandInput.of(text.stripTrailing());
        if (command.explicit() && command.start() == command.text().length()) {
            if (!command.text().strip().equalsIgnoreCase("/system")) throw new AgentException("UNSUPPORTED");
            if (this.root == null) throw new AgentException("UNSUPPORTED");
            if (!available.test(this.root.id())) throw new AgentException("CAPABILITY_UNAVAILABLE");
            return this.root;
        }
        Identifier owner = this.owners.get(command.name().toLowerCase(Locale.ROOT));
        if (owner == null) {
            if (command.explicit()) throw new AgentException("UNSUPPORTED");
            return null;
        }
        if (!available.test(owner)) throw new AgentException("CAPABILITY_UNAVAILABLE");
        AssistantCommandSource source = new AssistantCommandSource(command.text(), available);
        try {
            this.dispatcher.execute(command.reader(this.dispatcher.getRoot()), source);
            AgentAction action = source.action();
            if (action == null || !action.id().equals(owner)) throw new AgentException("INVALID_INPUT");
            // Native argument parsing still enters the same Codec and workflow checks as network actions.
            CodecIO.write(AgentAction.CODEC, action);
            return action;
        } catch (CommandSyntaxException error) {
            if (!command.explicit()) return null;
            throw new AgentException("INVALID_INPUT");
        } catch (RuntimeException error) {
            throw new AgentException("INVALID_INPUT");
        }
    }
}
