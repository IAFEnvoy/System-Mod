package com.iafenvoy.system.config.file;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Server-side per-success prices; each call snapshots its estimate before the network request.
 */
public record SearchPricingConfig(BigDecimal searchPerThousand, BigDecimal fetchPerThousand) {
    private static final Codec<BigDecimal> PRICE = Codec.STRING.comapFlatMap(value -> MiscCodecs.mapOrError(() -> {
        BigDecimal price = new BigDecimal(value);
        if (price.signum() < 0) throw new IllegalArgumentException("Negative search price");
        price.movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return price;
    }), BigDecimal::toPlainString);
    public static final Codec<SearchPricingConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            PRICE.lenientOptionalFieldOf("searchPerThousand", BigDecimal.valueOf(5)).forGetter(SearchPricingConfig::searchPerThousand),
            PRICE.lenientOptionalFieldOf("fetchPerThousand", BigDecimal.valueOf(5)).forGetter(SearchPricingConfig::fetchPerThousand)
    ).apply(i, SearchPricingConfig::new));
    public static final ConfigInstance<SearchPricingConfig> INSTANCE = ConfigInstance.of("system-search-pricing.json", CODEC,
            new SearchPricingConfig(BigDecimal.valueOf(5), BigDecimal.valueOf(5)));

    public long nanoYuan(boolean fetch) {
        return (fetch ? this.fetchPerThousand : this.searchPerThousand).movePointRight(6)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
