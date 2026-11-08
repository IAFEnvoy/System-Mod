package com.iafenvoy.saturn.api.playerview;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.PlayerViewMeta;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/**
 * A detached player snapshot. Reading a field never creates an attachment or resolves a live entity.
 */
public interface PlayerView {
    PlayerViewMeta meta();

    /**
     * Returns an independent decoded value, or an explicit reason why the value is unavailable.
     */
    <T> ViewValue<T> read(EntityValueGetter<T> getter);

    /**
     * Serialization context only; implementations must never resolve a live player to obtain it.
     */
    default DynamicOps<JsonElement> serializationOps() {
        return JsonOps.INSTANCE;
    }
}
