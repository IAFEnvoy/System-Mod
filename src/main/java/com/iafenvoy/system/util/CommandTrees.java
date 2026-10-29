package com.iafenvoy.system.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import java.util.function.Predicate;

/**
 * Detached native nodes prevent command merging from mutating cached declarations.
 */
public final class CommandTrees {
    private CommandTrees() {
    }

    public static <S> CommandNode<S> copy(CommandNode<S> node) {
        return copy(node, _ -> true);
    }

    public static <S> CommandDispatcher<S> available(CommandDispatcher<S> dispatcher, S source) {
        CommandDispatcher<S> filtered = new CommandDispatcher<>();
        dispatcher.getRoot().getChildren().stream().filter(node -> node.canUse(source))
                .forEach(node -> filtered.getRoot().addChild(copy(node, child -> child.canUse(source))));
        return filtered;
    }

    private static <S> CommandNode<S> copy(CommandNode<S> node, Predicate<CommandNode<S>> include) {
        // Brigadier completion does not apply requires(), so filter children before asking for candidates.
        ArgumentBuilder<S, ? extends ArgumentBuilder<S, ?>> builder = node.createBuilder();
        node.getChildren().stream().filter(include).forEach(child -> builder.then(copy(child, include)));
        return builder.build();
    }
}
