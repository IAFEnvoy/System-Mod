package com.iafenvoy.system.agent.commerce;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Payment methods supported by catalog prices. Serialized names remain stable for data packs.
 */
public enum PaymentType implements StringRepresentable {
    ITEMS("ITEMS"),
    SYSTEM_COIN("SYSTEM_COIN");

    public static final Codec<PaymentType> CODEC = StringRepresentable.fromEnum(PaymentType::values);
    private final String serializedName;

    PaymentType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.serializedName;
    }
}
