package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.progress.Milestone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Server-only definitions: predicates may match tags and item components without client supplied proof.
 */
public record CollectionDefinition(Component label, Component description, List<Entry> entries,
                                   List<Milestone> milestones,
                                   boolean enabled, EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<CollectionDefinition> CODEC = RecordCodecBuilder.<CollectionDefinition>create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(CollectionDefinition::label),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(CollectionDefinition::description),
            Entry.CODEC.listOf(1, 512).fieldOf("entries").forGetter(CollectionDefinition::entries),
            Milestone.CODEC.listOf(0, 128).optionalFieldOf("milestones", List.of()).forGetter(CollectionDefinition::milestones),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CollectionDefinition::enabled),
            EntryConditions.CODEC.forGetter(CollectionDefinition::conditions)
    ).apply(i, CollectionDefinition::new)).validate(value -> {
        if (value.entries.stream().map(Entry::id).distinct().count() != value.entries.size())
            return DataResult.error(() -> "Duplicate collection entry ID");
        if (value.milestones.stream().map(Milestone::id).distinct().count() != value.milestones.size())
            return DataResult.error(() -> "Duplicate collection milestone ID");
        if (value.milestones.stream().anyMatch(milestone -> milestone.target() > value.entries.size()))
            return DataResult.error(() -> "Unreachable collection milestone");
        return DataResult.success(value);
    });

    public CollectionDefinition {
        label = label.copy();
        description = description.copy();
        entries = List.copyOf(entries);
        milestones = List.copyOf(milestones);
    }

    public record Entry(Identifier id, Component label, ItemPredicate predicate, Optional<ItemStackTemplate> icon) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(Entry::label),
                ItemPredicate.CODEC.fieldOf("predicate").forGetter(Entry::predicate),
                ItemStackTemplate.CODEC.optionalFieldOf("icon").forGetter(Entry::icon)
        ).apply(i, Entry::new));

        public Entry {
            label = label.copy();
        }
    }
}
