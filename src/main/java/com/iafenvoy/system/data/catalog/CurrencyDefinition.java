package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStackTemplate;

/** Public currency metadata; balances are private player module data and never part of this registry. */
public record CurrencyDefinition(Component name, Component description, ItemStackTemplate icon, int order) {
    public static final Codec<Holder<CurrencyDefinition>> CODEC = RegistryFixedCodec.create(SystemResourceKeys.CURRENCY);
    public static final Codec<CurrencyDefinition> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("name").forGetter(CurrencyDefinition::name),
            MiscCodecs.TRANSLATABLE_COMPONENT.lenientOptionalFieldOf("description", Component.empty()).forGetter(CurrencyDefinition::description),
            ItemStackTemplate.CODEC.fieldOf("icon").forGetter(CurrencyDefinition::icon),
            Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(CurrencyDefinition::order)
    ).apply(i, CurrencyDefinition::new));

    public CurrencyDefinition {
        name = name.copy();
        description = description.copy();
    }
}
