package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record GainAirAction(int value) implements EntityAction {
    public static final MapCodec<GainAirAction> CODEC = Codec.INT.fieldOf("value").xmap(GainAirAction::new, GainAirAction::value);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        entity.setAirSupply(entity.getAirSupply() + this.value);
    }

    @Override
    public @NotNull MapCodec<GainAirAction> codec() {
        return CODEC;
    }
}
