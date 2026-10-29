package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record SetOnFireAction(int ticks) implements EntityAction {
    public static final MapCodec<SetOnFireAction> CODEC = Codec.INT.fieldOf("ticks").xmap(SetOnFireAction::new, SetOnFireAction::ticks);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        ctx.entity().setRemainingFireTicks(this.ticks);
    }

    @Override
    public @NotNull MapCodec<SetOnFireAction> codec() {
        return CODEC;
    }
}
