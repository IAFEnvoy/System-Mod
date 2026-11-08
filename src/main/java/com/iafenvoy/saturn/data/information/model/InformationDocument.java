package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.data.information.InformationGroup;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Predicate;

/**
 * An ordered list of evaluated entries; grouping and importance use the referenced definitions.
 */
public record InformationDocument(List<InformationEntry> rows) {
    public static final Codec<InformationDocument> CODEC = RecordCodecBuilder.create(i -> i.group(
            InformationEntry.CODEC.listOf().fieldOf("rows").forGetter(InformationDocument::rows)
    ).apply(i, InformationDocument::new));

    public InformationDocument {
        rows = List.copyOf(rows);
    }

    public List<InformationEntry> rows(Holder<InformationGroup> group) {
        return this.rows.stream().filter(row -> row.groups().contains(group))
                .sorted(InformationEntry.ORDER).toList();
    }

    public List<Holder<InformationGroup>> groups() {
        return this.rows.stream().flatMap(row -> row.groups().stream()).distinct()
                .sorted(InformationGroup.ORDER).toList();
    }

    /**
     * Keeps rows at or above the threshold in their original display order.
     */
    public InformationDocument withMinimumImportance(int minimum) {
        return new InformationDocument(this.rows.stream().filter(row -> row.importance() >= minimum).toList());
    }

    public InformationDocument filter(Predicate<ResourceLocation> allowedField) {
        return new InformationDocument(this.rows.stream()
                .filter(row -> row.sourceFields().stream().allMatch(allowedField)).toList());
    }
}
