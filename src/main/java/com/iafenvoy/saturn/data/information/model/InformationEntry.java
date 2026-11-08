package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.data.information.InformationGroup;
import com.iafenvoy.saturn.data.information.InformationRow;
import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluated text and value, with references to the original datapack definitions.
 */
public record InformationEntry(Holder<InformationRow> row, Set<Holder<InformationGroup>> groups, Component label,
                               InformationValue value, Optional<Component> tooltip) {
    public static final Codec<InformationEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            InformationRow.CODEC.fieldOf("row").forGetter(InformationEntry::row),
            CollectionCodecs.set(InformationGroup.CODEC).optionalFieldOf("groups", Set.of()).forGetter(InformationEntry::groups),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(InformationEntry::label),
            InformationValue.CODEC.fieldOf("value").forGetter(InformationEntry::value),
            ComponentSerialization.CODEC.optionalFieldOf("tooltip").forGetter(InformationEntry::tooltip)
    ).apply(i, InformationEntry::new));
    public static final Comparator<InformationEntry> ORDER = Comparator.comparingInt(InformationEntry::order).thenComparing(InformationEntry::id);
    public static final Comparator<InformationEntry> GROUP_ORDER = Comparator.comparing((InformationEntry entry) -> entry.groups.isEmpty())
            .thenComparing((left, right) -> {
                if (left.groups.isEmpty() || right.groups.isEmpty()) return 0;
                return InformationGroup.ORDER.compare(
                        left.groups.stream().min(InformationGroup.ORDER).orElseThrow(),
                        right.groups.stream().min(InformationGroup.ORDER).orElseThrow());
            }).thenComparing(ORDER);

    public InformationEntry {
        Objects.requireNonNull(row);
        groups = Set.copyOf(groups);
        label = ViewComponents.copy(label);
        Objects.requireNonNull(value);
        tooltip = tooltip.map(ViewComponents::copy);
    }

    public ResourceLocation id() {
        return this.row.unwrapKey().orElseThrow().location();
    }

    public int order() {
        return this.row.value().order();
    }

    public int importance() {
        return this.row.value().importance();
    }

    public InformationRow.Tone tone() {
        return this.row.value().tone();
    }

    public Set<ResourceLocation> sourceFields() {
        return this.row.value().sourceFields();
    }

    @Override
    public Component label() {
        return ViewComponents.copy(this.label);
    }

    @Override
    public Optional<Component> tooltip() {
        return this.tooltip.map(ViewComponents::copy);
    }

    public static Optional<InformationEntry> collect(Holder<InformationRow> row, Set<Holder<InformationGroup>> groups, InformationEvaluation context) {
        InformationRow definition = row.value();
        if (!definition.enabled()) return Optional.empty();
        if (definition.visible().isPresent()) {
            try {
                if (!context.evaluate(definition.visible().get()).flag()) return Optional.empty();
            } catch (ExpressionValue.Unavailable failure) {
                return Optional.empty();
            }
        }
        InformationValue value = context.evaluate(definition.value()).information();
        if (definition.missing() == InformationRow.MissingPolicy.HIDE && value instanceof InformationValue.Missing)
            return Optional.empty();
        Component label = definition.labelExpression().flatMap(expression -> text(context, expression)).orElseGet(definition::label);
        Optional<Component> tooltip = definition.tooltipExpression().flatMap(expression -> text(context, expression)).or(definition::tooltip);
        return Optional.of(new InformationEntry(row, groups, label, value, tooltip));
    }

    private static Optional<Component> text(InformationEvaluation context, InformationExpression expression) {
        try {
            return Optional.of(context.evaluate(expression).text());
        } catch (ExpressionValue.Unavailable failure) {
            return Optional.empty();
        }
    }
}
