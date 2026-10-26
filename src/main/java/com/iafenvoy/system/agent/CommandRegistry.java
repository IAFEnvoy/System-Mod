package com.iafenvoy.system.agent;

import java.util.*;
import java.util.function.Function;

/**
 * Exact parsing, help and completion share the same registered aliases and argument declarations.
 */
public final class CommandRegistry {
    private final Map<String, Function<String, TypedPayload<?>>> commands = new LinkedHashMap<>();
    private final List<String> suggestions = new ArrayList<>(), usages = new ArrayList<>();
    private TypedPayload<?> root;

    <I> void validate(List<AgentCommand<I>> commands) {
        Set<String> names = new HashSet<>(this.commands.keySet());
        for (AgentCommand<I> command : commands)
            for (String name : command.names())
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate command alias " + name);
    }

    <I> void register(TypedPayload.Type<I> type, List<AgentCommand<I>> commands) {
        for (AgentCommand<I> command : commands) {
            for (String name : command.names()) {
                this.commands.put(name, argument -> type.of(command.parser().apply(argument)));
                this.suggestions.add(name + (command.arguments().isBlank() ? "" : " "));
            }
            this.usages.add(String.join(" | ", command.names().stream().map(name -> "/" + name).toList())
                    + (command.arguments().isBlank() ? "" : " " + command.arguments()));
        }
    }

    void root(TypedPayload<?> input) {
        if (this.root != null) throw new IllegalArgumentException("Duplicate root command");
        this.root = input;
    }

    public List<String> suggestions() {
        return List.copyOf(this.suggestions);
    }

    public List<String> usages() {
        return List.copyOf(this.usages);
    }

    public TypedPayload<?> parse(String text) {
        String command = text.strip();
        boolean explicit = command.startsWith("/");
        if (explicit) command = command.substring(1);
        int end = wordEnd(command);
        if (command.substring(0, end).equalsIgnoreCase("system") && explicit) {
            command = command.substring(end).strip();
            if (command.isEmpty()) {
                if (this.root == null) throw new AgentException("UNSUPPORTED");
                return this.root;
            }
            if (command.startsWith("/")) command = command.substring(1);
            end = wordEnd(command);
        }
        Function<String, TypedPayload<?>> parser = this.commands.get(command.substring(0, end).toLowerCase(Locale.ROOT));
        if (parser == null) {
            if (explicit) throw new AgentException("UNSUPPORTED");
            return null;
        }
        try {
            TypedPayload<?> input = parser.apply(command.substring(end).strip());
            // Java parsers must meet the same codec constraints as packet and model inputs.
            input.json();
            return input;
        } catch (RuntimeException error) {
            throw new AgentException("INVALID_INPUT");
        }
    }

    private static int wordEnd(String text) {
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        return end;
    }
}
