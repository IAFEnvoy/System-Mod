package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record SetFallDistanceAction(float distance) implements EntityAction {
    public static final MapCodec<SetFallDistanceAction> CODEC = Codec.FLOAT.fieldOf("distance").xmap(SetFallDistanceAction::new, SetFallDistanceAction::distance);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        ctx.entity().fallDistance = this.distance;
    }

    @Override
    public @NotNull MapCodec<SetFallDistanceAction> codec() {
        return CODEC;
    }
}
