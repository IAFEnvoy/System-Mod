package com.iafenvoy.saturn.api.playerview;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;

/**
 * Writes detached snapshot values, encoding immediately instead of retaining mutable references.
 */
public interface PlayerViewWriter {
    <T> void put(EntityValueGetter<T> getter, T value, ValueOrigin origin);

    void missing(EntityValueGetter<?> getter, MissingReason reason);

    boolean contains(EntityValueGetter<?> getter);

    default <T> void put(EntityValueGetter<T> getter, T value) {
        this.put(getter, value, ValueOrigin.CAPTURED);
    }
}
