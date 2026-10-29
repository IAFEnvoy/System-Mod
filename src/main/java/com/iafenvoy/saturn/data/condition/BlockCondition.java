package com.iafenvoy.saturn.data.condition;

import com.iafenvoy.saturn.data.condition.builtin.block.meta.AndBlockCondition;
import com.iafenvoy.saturn.data.context.Context;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface BlockCondition {
    Codec<BlockCondition> SINGLE_CODEC = SaturnRegistries.BLOCK_CONDITION_TYPE.byNameCodec().dispatch("type", BlockCondition::codec, Function.identity());
    Codec<BlockCondition> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(AndBlockCondition::new, Function.identity()), Either::right);

    static MapCodec<BlockCondition> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, AlwaysCondition.INSTANCE);
    }

    @NotNull MapCodec<? extends BlockCondition> codec();

    boolean test(@NotNull BlockConditionContext context);

    default boolean test(Level level, BlockPos pos, Context parent) {
        return this.test(parent.copyTo(new BlockConditionContext(level, pos, parent.formula())));
    }

    default boolean test(Level level, BlockPos pos, FormulaContext formula) {
        return this.test(new BlockConditionContext(level, pos, formula));
    }
}
