package com.iafenvoy.saturn.data.information.expression;

import com.iafenvoy.saturn.data.information.expression.builtin.ConstantExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetters;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Register a MapCodec in INFORMATION_EXPRESSION_TYPE to add a reusable, read-only operation.
 */
public interface InformationExpression {
    Codec<ConstantExpression> CONSTANT_CODEC = Codec.PASSTHROUGH.xmap(ConstantExpression::new, ConstantExpression::value);
    Codec<InformationExpression> TYPED_CODEC = SaturnRegistries.INFORMATION_EXPRESSION_TYPE.byNameCodec().dispatch("type", InformationExpression::codec, Function.identity());
    Codec<InformationExpression> CODEC = Codec.either(TYPED_CODEC, CONSTANT_CODEC).xmap(e -> e.map(Function.identity(), Function.identity()), Either::left);

    MapCodec<? extends InformationExpression> codec();

    ExpressionValue evaluate(InformationEvaluation context);

    /**
     * Include dependencies of all branches, including branches not selected at runtime.
     */
    Set<EntityValueGetter<?>> getters();

    default Set<ResourceLocation> sourceFields() {
        Set<ResourceLocation> result = new HashSet<>();
        this.getters().forEach(getter -> result.addAll(EntityValueGetters.sources(getter)));
        return Set.copyOf(result);
    }
}
