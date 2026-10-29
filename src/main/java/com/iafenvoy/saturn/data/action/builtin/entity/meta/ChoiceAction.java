package com.iafenvoy.saturn.data.action.builtin.entity.meta;

import com.iafenvoy.saturn.data.Weighted;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ChoiceAction(List<Weighted<EntityAction>> actions) implements EntityAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(EntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        Weighted<EntityAction> entry = Weighted.select(this.actions, entity.getRandom());
        if (entry != null) entry.value().execute(entity, ctx);
    }

    @Override
    public @NotNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
