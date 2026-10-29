package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record SetNoGravityAction(boolean noGravity) implements EntityAction {
    public static final MapCodec<SetNoGravityAction> CODEC = Codec.BOOL.optionalFieldOf("no_gravity", true).xmap(SetNoGravityAction::new, SetNoGravityAction::noGravity);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        ctx.entity().setNoGravity(this.noGravity);
    }

    @Override
    public @NotNull MapCodec<SetNoGravityAction> codec() {
        return CODEC;
    }
}
