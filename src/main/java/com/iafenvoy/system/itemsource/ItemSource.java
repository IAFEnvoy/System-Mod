package com.iafenvoy.system.itemsource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

/**
 * One way to obtain an item. Providers fill these fields and the codec is what callers receive as JSON.
 *
 * @param kind    source family, e.g. {@code recipe} or {@code loot}
 * @param id      registry ID of the recipe or loot table that produces the item
 * @param type    free-form sub-type, e.g. the recipe type ID; empty when the provider has none
 * @param station item IDs required to use this source, never consumed
 * @param inputs  consumed slots, empty when the provider cannot describe them
 * @param outputs produced slots, always containing the queried item
 * @param notes   short human-readable hints such as a loot weight; never translated
 */
public record ItemSource(String kind, String id, String type, List<String> station,
                         List<ItemSourceSlot> inputs, List<ItemSourceSlot> outputs, List<String> notes) {
    public static final Codec<ItemSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(ItemSource::kind),
            Codec.STRING.fieldOf("id").forGetter(ItemSource::id),
            Codec.STRING.optionalFieldOf("type", "").forGetter(ItemSource::type),
            Codec.STRING.listOf().optionalFieldOf("station", List.of()).forGetter(ItemSource::station),
            ItemSourceSlot.CODEC.listOf().optionalFieldOf("inputs", List.of()).forGetter(ItemSource::inputs),
            ItemSourceSlot.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(ItemSource::outputs),
            Codec.STRING.listOf().optionalFieldOf("notes", List.of()).forGetter(ItemSource::notes)
    ).apply(i, ItemSource::new));

    public ItemSource {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(id);
        type = Objects.requireNonNull(type);
        station = List.copyOf(station);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        notes = List.copyOf(notes);
    }
}
