package com.iafenvoy.saturn.data.action.builtin.bientity;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public record HealTargetBiEntityAction(NumberProvider amount) implements BiEntityAction {
    public static final MapCodec<HealTargetBiEntityAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(HealTargetBiEntityAction::new, HealTargetBiEntityAction::amount);

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        if (ctx.target() instanceof LivingEntity living) {
            double amount = MiscUtil.positive(this.amount.evaluate(ctx.formula()));
            if (amount > 0.0D) living.heal((float) amount);
        }
    }

    @Override
    public @NotNull MapCodec<HealTargetBiEntityAction> codec() {
        return CODEC;
    }
}
