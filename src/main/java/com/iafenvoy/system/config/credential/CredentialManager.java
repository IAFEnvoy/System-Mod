package com.iafenvoy.system.config.credential;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * One service kind maps credential implementations to typed constructors.
 * Credentials and service instances remain owned by the caller.
 */
public final class CredentialManager<S> {
    private final Map<Class<? extends Credential>, Function<Credential, ? extends S>> factories = new LinkedHashMap<>();

    /**
     * Constructor references accept the selected credential; lambdas can capture additional dependencies.
     * Each credential implementation has exactly one service factory in this manager.
     */
    public synchronized <C extends Credential> CredentialManager<S> register(Class<C> type, Function<? super C, ? extends S> factory) {
        Objects.requireNonNull(type, "Credential type");
        Objects.requireNonNull(factory, "Service factory");
        if (this.factories.putIfAbsent(type, credential -> factory.apply(type.cast(credential))) != null)
            throw new IllegalArgumentException("Duplicate credential service factory " + type.getName());
        return this;
    }

    /** A selection control can filter credentials without constructing services or reading their secrets. */
    public synchronized boolean supports(Credential credential) {
        return this.factories.containsKey(credential.getClass());
    }

    /** Registration order is retained; callers cannot modify the manager through the returned snapshot. */
    public synchronized Set<Class<? extends Credential>> credentialTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.factories.keySet()));
    }

    /**
     * Creates a service for this exact credential instance, or returns empty for an unsupported type.
     * Factories run outside the registration lock; construction failures propagate to the caller.
     * Services are not cached or closed here, so request-scoped providers can be created independently.
     */
    public Optional<S> create(Credential credential) {
        Function<Credential, ? extends S> factory;
        synchronized (this) {
            factory = this.factories.get(credential.getClass());
        }
        if (factory == null) return Optional.empty();
        return Optional.of(Objects.requireNonNull(factory.apply(credential), "Credential service factory returned null"));
    }
}
