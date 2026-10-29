package com.iafenvoy.system.agent;

import com.iafenvoy.system.command.AssistantCommandSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Workflows supply native Brigadier nodes; execution only produces an action for the normal Agent pipeline.
 */
public record AgentCommand<I extends AgentAction>(List<String> names,
                                                  Function<String, LiteralArgumentBuilder<AssistantCommandSource>> node) {
    public AgentCommand {
        names = List.copyOf(names);
        if (names.isEmpty() || names.stream().anyMatch(name -> name.isBlank() || name.equals("system") || name.contains("/")
                || name.chars().anyMatch(Character::isWhitespace) || !name.equals(name.toLowerCase(Locale.ROOT))))
            throw new IllegalArgumentException("Invalid command names");
        Objects.requireNonNull(node);
    }

    public static <I extends AgentAction> AgentCommand<I> literal(I input, String... names) {
        Objects.requireNonNull(input);
        return new AgentCommand<>(List.of(names), name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name)
                .executes(context -> context.getSource().action(input)));
    }

    public static <I extends AgentAction> AgentCommand<I> argument(String usage, Function<String, I> parser, String... names) {
        boolean optional = usage.startsWith("[");
        String argument = usage.substring(1, usage.length() - 1);
        return new AgentCommand<>(List.of(names), name -> {
            LiteralArgumentBuilder<AssistantCommandSource> root = LiteralArgumentBuilder.literal(name);
            if (optional) root.executes(context -> context.getSource().action(parser.apply("")));
            return root.then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument(argument, StringArgumentType.greedyString())
                    .executes(context -> context.getSource().action(parser.apply(StringArgumentType.getString(context, argument).strip()))));
        });
    }

    public static <I extends AgentAction> AgentCommand<I> identifier(String argument, Function<ResourceLocation, I> parser, String... names) {
        return new AgentCommand<>(List.of(names), name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name)
                .then(RequiredArgumentBuilder.<AssistantCommandSource, ResourceLocation>argument(argument, ResourceLocationArgument.id())
                        .executes(context -> context.getSource().action(parser.apply(context.getArgument(argument, ResourceLocation.class))))));
    }
}
