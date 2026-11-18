package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Direction changes presentation and routing; both directions retain the same trusted cost/reward settlement.
 */
public enum ShopTrade implements StringRepresentable {
    PURCHASE, BUYBACK;

    public static final Codec<ShopTrade> CODEC = StringRepresentable.fromEnum(ShopTrade::values);
    public static final SchemaCodecs.Value<ShopTrade> SCHEMA = SchemaCodecs.matching("purchase|buyback")
            .xmap(ShopTrade::parse, ShopTrade::getSerializedName);

    public static ShopTrade parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public String key(String suffix) {
        return "system.shop." + this.getSerializedName() + "." + suffix;
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
