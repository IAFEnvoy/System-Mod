package com.iafenvoy.saturn.data.information;

import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.ViewComponents;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * An executable recipe, never a serialized player snapshot. The registry supplies its row id.
 */
public record InformationRow(int order, int importance, boolean enabled,
                             Component label, Optional<InformationExpression> labelExpression,
                             InformationExpression value, Optional<Component> tooltip,
                             Optional<InformationExpression> tooltipExpression, Tone tone,
                             Optional<InformationExpression> visible, MissingPolicy missing) {
    public static final Codec<Holder<InformationRow>> CODEC = RegistryFixedCodec.create(SaturnResourceKeys.INFORMATION_ROW);
    public static final Codec<InformationRow> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("order", 0).forGetter(InformationRow::order),
            Codec.INT.optionalFieldOf("importance", 0).forGetter(InformationRow::importance),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(InformationRow::enabled),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(InformationRow::label),
            InformationExpression.CODEC.optionalFieldOf("label_expression").forGetter(InformationRow::labelExpression),
            InformationExpression.CODEC.fieldOf("value").forGetter(InformationRow::value),
            ComponentSerialization.CODEC.optionalFieldOf("tooltip").forGetter(InformationRow::tooltip),
            InformationExpression.CODEC.optionalFieldOf("tooltip_expression").forGetter(InformationRow::tooltipExpression),
            Tone.CODEC.optionalFieldOf("tone", Tone.NORMAL).forGetter(InformationRow::tone),
            InformationExpression.CODEC.optionalFieldOf("visible").forGetter(InformationRow::visible),
            MissingPolicy.CODEC.optionalFieldOf("missing", MissingPolicy.SHOW).forGetter(InformationRow::missing)
    ).apply(i, InformationRow::new));

    public InformationRow {
        label = ViewComponents.copy(label);
        tooltip = tooltip.map(ViewComponents::copy);
    }

    @Override
    public Component label() {
        return ViewComponents.copy(this.label);
    }

    @Override
    public Optional<Component> tooltip() {
        return this.tooltip.map(ViewComponents::copy);
    }

    public Set<EntityValueGetter<?>> getters() {
        Set<EntityValueGetter<?>> result = new HashSet<>(this.value.getters());
        this.visible.ifPresent(expression -> result.addAll(expression.getters()));
        this.labelExpression.ifPresent(expression -> result.addAll(expression.getters()));
        this.tooltipExpression.ifPresent(expression -> result.addAll(expression.getters()));
        return Set.copyOf(result);
    }

    public Set<ResourceLocation> sourceFields() {
        Set<ResourceLocation> result = new HashSet<>(this.value.sourceFields());
        this.visible.ifPresent(expression -> result.addAll(expression.sourceFields()));
        this.labelExpression.ifPresent(expression -> result.addAll(expression.sourceFields()));
        this.tooltipExpression.ifPresent(expression -> result.addAll(expression.sourceFields()));
        return Set.copyOf(result);
    }

    public enum Tone {
        NORMAL, POSITIVE, WARNING;
        public static final Codec<Tone> CODEC = ExtraEnumCodecs.enumCodec(Tone.class);
    }

    public enum MissingPolicy {
        SHOW, HIDE;
        public static final Codec<MissingPolicy> CODEC = ExtraEnumCodecs.enumCodec(MissingPolicy.class);
    }
}
