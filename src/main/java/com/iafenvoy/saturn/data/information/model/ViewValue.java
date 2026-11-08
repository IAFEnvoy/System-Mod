package com.iafenvoy.saturn.data.information.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record ViewValue<T>(Optional<T> value, Optional<MissingReason> missingReason, ValueOrigin origin) {
    public ViewValue {
        Objects.requireNonNull(value);
        Objects.requireNonNull(missingReason);
        Objects.requireNonNull(origin);
        if (value.isPresent() == missingReason.isPresent())
            throw new IllegalArgumentException("Expected a value or a missing reason");
    }

    public static <T> ViewValue<T> known(T value, ValueOrigin origin) {
        return new ViewValue<>(Optional.of(value), Optional.empty(), origin);
    }

    public static <T> ViewValue<T> missing(MissingReason reason) {
        return new ViewValue<>(Optional.empty(), Optional.of(reason), ValueOrigin.SAVED);
    }

    public boolean isKnown() {
        return this.value.isPresent();
    }

    public <R> ViewValue<R> map(Function<? super T, ? extends R> mapper) {
        return this.value.<ViewValue<R>>map(value -> known(mapper.apply(value), this.origin))
                .orElseGet(() -> missing(this.missingReason.orElseThrow()));
    }
}
