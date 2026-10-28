package com.iafenvoy.system.module.client;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Local aliases and argument handlers are supplied by features; execution and completion use the same table.
 */
public final class ClientCommandRegistry {
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<Pattern> patterns = new ArrayList<>();
    private boolean frozen;

    public void register(Runnable action, String... aliases) {
        this.register(() -> true, action, aliases);
    }

    public void register(BooleanSupplier available, Runnable action, String... aliases) {
        this.checkMutable();
        Set<String> names = new HashSet<>();
        for (String alias : aliases)
            if (!alias.equals(alias.toLowerCase(Locale.ROOT)) || alias.isBlank() || !names.add(alias) || this.commands.containsKey(alias))
                throw new IllegalArgumentException("Duplicate or invalid client command " + alias);
        for (String alias : aliases) this.commands.put(alias, new Command(available, action));
    }

    public void pattern(Function<String, Boolean> handler, String... suggestions) {
        this.pattern(() -> true, handler, suggestions);
    }

    public void pattern(BooleanSupplier available, Function<String, Boolean> handler, String... suggestions) {
        this.checkMutable();
        this.patterns.add(new Pattern(available, handler, List.of(suggestions)));
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client command registration has finished");
    }

    void freeze() {
        this.frozen = true;
    }

    public List<String> suggestions() {
        List<String> result = new ArrayList<>();
        this.commands.forEach((alias, command) -> {
            if (command.available().getAsBoolean()) result.add(alias);
        });
        for (Pattern pattern : this.patterns)
            if (pattern.available().getAsBoolean()) result.addAll(pattern.suggestions());
        return List.copyOf(result);
    }

    public boolean execute(String text) {
        String command = text.strip();
        if (command.regionMatches(true, 0, "/system ", 0, 8)) command = command.substring(8).strip();
        if (command.startsWith("/")) command = command.substring(1);
        Command handler = this.commands.get(command.toLowerCase(Locale.ROOT));
        if (handler != null && handler.available().getAsBoolean()) {
            handler.action().run();
            return true;
        }
        for (Pattern pattern : this.patterns)
            if (pattern.available().getAsBoolean() && pattern.handler().apply(command)) return true;
        return false;
    }

    private record Command(BooleanSupplier available, Runnable action) {
    }

    private record Pattern(BooleanSupplier available, Function<String, Boolean> handler, List<String> suggestions) {
    }
}
