package com.iafenvoy.system.module.client;

import java.util.*;
import java.util.function.Function;

/**
 * Local aliases and argument handlers are supplied by features; execution and completion use the same table.
 */
public final class ClientCommandRegistry {
    private final Map<String, Runnable> commands = new LinkedHashMap<>();
    private final List<String> argumentSuggestions = new ArrayList<>();
    private final List<Function<String, Boolean>> patterns = new ArrayList<>();
    private boolean frozen;

    public void register(Runnable action, String... aliases) {
        this.checkMutable();
        Set<String> names = new HashSet<>();
        for (String alias : aliases)
            if (!alias.equals(alias.toLowerCase(Locale.ROOT)) || alias.isBlank() || !names.add(alias) || this.commands.containsKey(alias))
                throw new IllegalArgumentException("Duplicate or invalid client command " + alias);
        for (String alias : aliases) this.commands.put(alias, action);
    }

    public void pattern(Function<String, Boolean> handler, String... suggestions) {
        this.checkMutable();
        this.patterns.add(handler);
        this.argumentSuggestions.addAll(List.of(suggestions));
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client command registration has finished");
    }

    void freeze() {
        this.frozen = true;
    }

    public List<String> suggestions() {
        List<String> result = new ArrayList<>(this.commands.keySet());
        result.addAll(this.argumentSuggestions);
        return List.copyOf(result);
    }

    public boolean execute(String text) {
        String command = text.strip();
        if (command.regionMatches(true, 0, "/system ", 0, 8)) command = command.substring(8).strip();
        if (command.startsWith("/")) command = command.substring(1);
        Runnable handler = this.commands.get(command.toLowerCase(Locale.ROOT));
        if (handler != null) {
            handler.run();
            return true;
        }
        for (Function<String, Boolean> pattern : this.patterns)
            if (pattern.apply(command)) return true;
        return false;
    }
}
