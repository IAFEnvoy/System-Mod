package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;
import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.entries;

public record ListExpression(InformationExpression source, String as,
                             InformationExpression element) implements InformationExpression {
    public static final MapCodec<ListExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("source").forGetter(ListExpression::source),
            Codec.STRING.optionalFieldOf("as", "item").forGetter(ListExpression::as),
            InformationExpression.CODEC.fieldOf("element").forGetter(ListExpression::element)
    ).apply(i, ListExpression::new));

    @Override
    public MapCodec<ListExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        List<InformationValue> values = entries(context.evaluate(this.source)).stream()
                .map(entry -> context.withLocal(this.as, entry.getValue(), entry.getKey()).evaluate(this.element).information()).toList();
        return new ExpressionValue.Display(new InformationValue.Values(values));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.source, this.element);
    }
}
