package com.iafenvoy.system.agent;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Exact parsing, help and completion share the same registered aliases and argument declarations.
 */
public final class CommandRegistry {
    private final Map<String, Function<String, AgentAction>> commands = new LinkedHashMap<>();
    private final Map<String, Identifier> suggestions = new LinkedHashMap<>(), usages = new LinkedHashMap<>();
    private AgentAction root;

    <I extends AgentAction> void validate(List<AgentCommand<I>> commands) {
        Set<String> names = new HashSet<>(this.commands.keySet());
        for (AgentCommand<I> command : commands)
            for (String name : command.names())
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate command alias " + name);
    }

    <I extends AgentAction> void register(Identifier id, MapCodec<I> codec, List<AgentCommand<I>> commands) {
        for (AgentCommand<I> command : commands) {
            for (String name : command.names()) {
                this.commands.put(name, argument -> AgentAction.validate(codec, command.parser().apply(argument)));
                this.suggestions.put(name + (command.arguments().isBlank() ? "" : " "), id);
            }
            this.usages.put(String.join(" | ", command.names().stream().map(name -> "/" + name).toList())
                    + (command.arguments().isBlank() ? "" : " " + command.arguments()), id);
        }
    }

    void root(AgentAction input) {
        if (this.root != null) throw new IllegalArgumentException("Duplicate root command");
        this.root = input;
    }

    public List<String> suggestions() {
        return this.suggestions(_ -> true);
    }

    public List<String> suggestions(Predicate<Identifier> available) {
        return this.suggestions.entrySet().stream().filter(entry -> available.test(entry.getValue())).map(Map.Entry::getKey).toList();
    }

    public List<String> usages() {
        return this.usages(_ -> true);
    }

    public List<String> usages(Predicate<Identifier> available) {
        return this.usages.entrySet().stream().filter(entry -> available.test(entry.getValue())).map(Map.Entry::getKey).toList();
    }

    public AgentAction parse(String text) {
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
        Function<String, AgentAction> parser = this.commands.get(command.substring(0, end).toLowerCase(Locale.ROOT));
        if (parser == null) {
            if (explicit) throw new AgentException("UNSUPPORTED");
            return null;
        }
        try {
            return parser.apply(command.substring(end).strip());
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
