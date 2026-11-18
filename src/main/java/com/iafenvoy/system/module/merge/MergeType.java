package com.iafenvoy.system.module.merge;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Function;

/**
 * A canonical Java capability key; instances are compared by identity, never by erased generic names.
 */
public final class MergeType<C> {
    private final Identifier id;
    private final Class<C> contributionType;
    private final Function<MergeContext<C>, MergeRuntime> factory;

    public MergeType(Identifier id, Class<C> contributionType, Function<MergeContext<C>, MergeRuntime> factory) {
        this.id = Objects.requireNonNull(id);
        this.contributionType = Objects.requireNonNull(contributionType);
        this.factory = Objects.requireNonNull(factory);
    }

    public Identifier id() {
        return this.id;
    }

    public Class<C> contributionType() {
        return this.contributionType;
    }

    public MergeRuntime create(MergeContext<C> context) {
        return Objects.requireNonNull(this.factory.apply(context));
    }
}
