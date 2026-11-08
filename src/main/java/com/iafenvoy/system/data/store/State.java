package com.iafenvoy.system.data.store;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

public enum State implements StringRepresentable {
    QUEUED, RUNNING, WAITING, DONE, SUCCEEDED, FAILED, PARTIALLY_SUCCEEDED;

    public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

    @Override
    public @NonNull String getSerializedName() {
        // Event tasks and step receipts share states while preserving their stored names.
        return this.name();
    }
}
