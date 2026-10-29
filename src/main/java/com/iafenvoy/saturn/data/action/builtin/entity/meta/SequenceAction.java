package com.iafenvoy.saturn.data.action.builtin.entity.meta;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SequenceAction(List<EntityAction> actions) implements EntityAction {
    public static final MapCodec<SequenceAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceAction::new, SequenceAction::actions);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        this.actions.forEach(action -> action.execute(entity, ctx));
    }

    @Override
    public @NotNull MapCodec<SequenceAction> codec() {
        return CODEC;
    }
}
