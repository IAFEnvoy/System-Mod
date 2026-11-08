package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.data.information.model.PlayerViewItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record ItemsExpression(InformationExpression value) implements InformationExpression {
    public static final MapCodec<ItemsExpression> CODEC = InformationExpression.CODEC.fieldOf("value").xmap(ItemsExpression::new, ItemsExpression::value);

    @Override
    public MapCodec<ItemsExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return new ExpressionValue.Display(new InformationValue.Items(context.evaluate(this.value).decode(Codec.unboundedMap(Codec.STRING, PlayerViewItem.CODEC))));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value);
    }
}
