package com.iafenvoy.system.agent;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** A module-local command declaration; the registry binds its typed arguments to the owning module. */
public record AgentCommand<I>(List<String> names, String arguments, Function<String, I> parser) {
    public AgentCommand {
        names = List.copyOf(names);
        if (names.isEmpty() || names.stream().anyMatch(name -> name.isBlank() || name.length() > 48 || name.equals("system") || name.contains("/")
                || name.chars().anyMatch(Character::isWhitespace) || !name.equals(name.toLowerCase(Locale.ROOT))))
            throw new IllegalArgumentException("Invalid command names");
        Objects.requireNonNull(arguments);
        if (arguments.length() > 128 || arguments.contains("\n") || arguments.contains("\r"))
            throw new IllegalArgumentException("Invalid command usage");
        Objects.requireNonNull(parser);
    }

    public static <I> AgentCommand<I> literal(I input, String... names) {
        Objects.requireNonNull(input);
        return new AgentCommand<>(List.of(names), "", argument -> {
            if (!argument.isEmpty()) throw new AgentException("INVALID_INPUT");
            return input;
        });
    }

    public static <I> AgentCommand<I> argument(String usage, Function<String, I> parser, String... names) {
        return new AgentCommand<>(List.of(names), usage, parser);
    }
}
