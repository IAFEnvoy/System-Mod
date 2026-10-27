package com.iafenvoy.system.agent.commerce;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Distinguishes confirmed operations and their inbox IDs without allowing cross-purpose quotes.
 */
public enum PurchaseType implements StringRepresentable {
    SHOP("shop"),
    LOTTERY("lottery");

    public static final Codec<PurchaseType> CODEC = StringRepresentable.fromEnum(PurchaseType::values);
    private final String name;

    PurchaseType(String name) {
        this.name = name;
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name;
    }

    public String entitlement(String orderId) {
        return this.name + ":" + orderId;
    }
}
