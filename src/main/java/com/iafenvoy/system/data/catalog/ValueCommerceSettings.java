package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * Value conversion and item selection shared by generated purchase and buyback sources.
 */
public record ValueCommerceSettings(Holder<CurrencyDefinition> currency, double valuePerCurrency,
                                    double priceMultiplier, boolean hideFixed,
                                    Optional<List<ItemMatcher.Entry>> items, List<ItemMatcher.Entry> excludedItems) {
    public static final Codec<Double> MULTIPLIER = Codec.doubleRange(0.000001, 1.0e9);
    public static final MapCodec<ValueCommerceSettings> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CurrencyDefinition.CODEC.fieldOf("currency").forGetter(ValueCommerceSettings::currency),
            MULTIPLIER.optionalFieldOf("value_per_currency", 1.0).forGetter(ValueCommerceSettings::valuePerCurrency),
            MULTIPLIER.optionalFieldOf("price_multiplier", 1.0).forGetter(ValueCommerceSettings::priceMultiplier),
            Codec.BOOL.optionalFieldOf("hide_fixed", true).forGetter(ValueCommerceSettings::hideFixed),
            ItemMatcher.ENTRIES_CODEC.optionalFieldOf("items").forGetter(ValueCommerceSettings::items),
            ItemMatcher.ENTRIES_CODEC.optionalFieldOf("excluded_items", List.of()).forGetter(ValueCommerceSettings::excludedItems)
    ).apply(i, ValueCommerceSettings::new));

    public ValueCommerceSettings {
        items = items.map(List::copyOf);
        excludedItems = List.copyOf(excludedItems);
    }
}
