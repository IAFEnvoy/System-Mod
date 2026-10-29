package com.iafenvoy.saturn.data.action.builtin.bientity.meta;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Applies an entity action to the actor of a bi-entity interaction.
 */
public record ActorAction(EntityAction action) implements BiEntityAction {
    public static final MapCodec<ActorAction> CODEC = EntityAction.CODEC.fieldOf("action").xmap(ActorAction::new, ActorAction::action);

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        this.action.execute(ctx.actor(), ctx);
    }

    @Override
    public @NotNull MapCodec<ActorAction> codec() {
        return CODEC;
    }
}
