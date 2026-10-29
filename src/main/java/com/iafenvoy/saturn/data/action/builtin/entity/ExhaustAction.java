package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record ExhaustAction(float amount) implements EntityAction {
    public static final MapCodec<ExhaustAction> CODEC = Codec.FLOAT.fieldOf("amount").xmap(ExhaustAction::new, ExhaustAction::amount);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        if (ctx.entity() instanceof Player player && this.amount > 0.0F) player.causeFoodExhaustion(this.amount);
    }

    @Override
    public @NotNull MapCodec<ExhaustAction> codec() {
        return CODEC;
    }
}
