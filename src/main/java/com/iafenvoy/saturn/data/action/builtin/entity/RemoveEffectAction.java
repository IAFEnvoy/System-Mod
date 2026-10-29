package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public record RemoveEffectAction(Holder<MobEffect> effect) implements EntityAction {
    public static final MapCodec<RemoveEffectAction> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(RemoveEffectAction::new, RemoveEffectAction::effect);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        if (ctx.entity() instanceof LivingEntity living)
            living.removeEffect(this.effect);
    }

    @Override
    public @NotNull MapCodec<RemoveEffectAction> codec() {
        return CODEC;
    }
}
