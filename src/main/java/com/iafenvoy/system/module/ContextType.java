package com.iafenvoy.system.module;

import java.util.Objects;

/**
 * Identity-based service key; the same key carries its value type through registration and lookup.
 */
@SuppressWarnings("unused")
public final class ContextType<T> {
    private final String name;

    public ContextType(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
