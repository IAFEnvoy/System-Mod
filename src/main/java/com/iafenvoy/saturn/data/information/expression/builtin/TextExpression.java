package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record TextExpression(InformationExpression value, boolean component) implements InformationExpression {
    public static final MapCodec<TextExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("value").forGetter(TextExpression::value),
            Codec.BOOL.optionalFieldOf("component", false).forGetter(TextExpression::component)
    ).apply(i, TextExpression::new));

    @Override
    public MapCodec<TextExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        ExpressionValue value = context.evaluate(this.value);
        return new ExpressionValue.Display(new InformationValue.Text(this.component ? value.decode(ComponentSerialization.CODEC) : value.text()));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value);
    }
}
