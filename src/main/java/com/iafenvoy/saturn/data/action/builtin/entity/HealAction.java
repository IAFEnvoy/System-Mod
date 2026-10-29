package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public record HealAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<HealAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(NumberProvider.CODEC.fieldOf("amount").forGetter(HealAction::amount)).apply(i, HealAction::new));

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        if (ctx.entity() instanceof LivingEntity living) {
            double value = MiscUtil.positive(this.amount.evaluate(ctx.formula()));
            if (value > 0.0D) living.heal((float) value);
        }
    }

    @Override
    public @NotNull MapCodec<HealAction> codec() {
        return CODEC;
    }
}
