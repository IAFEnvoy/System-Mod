package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import java.util.Set;

public record ConstantExpression(Dynamic<?> value) implements InformationExpression {
    public static final MapCodec<ConstantExpression> CODEC = Codec.PASSTHROUGH.fieldOf("value").xmap(ConstantExpression::new, ConstantExpression::value);

    public ConstantExpression {
        value = new Dynamic<>(JsonOps.INSTANCE, value.convert(JsonOps.INSTANCE).getValue().deepCopy());
    }

    @Override
    public Dynamic<?> value() {
        return new Dynamic<>(JsonOps.INSTANCE, this.value.convert(JsonOps.INSTANCE).getValue().deepCopy());
    }

    @Override
    public MapCodec<ConstantExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return context.data(this.value.convert(JsonOps.INSTANCE).getValue());
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return Set.of();
    }
}
