package com.iafenvoy.saturn.data.action.builtin.bientity.meta;

import com.iafenvoy.saturn.data.Weighted;
import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ChoiceAction(List<Weighted<BiEntityAction>> actions) implements BiEntityAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(BiEntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Weighted<BiEntityAction> entry = Weighted.select(this.actions, actor.getRandom());
        if (entry != null) entry.value().execute(actor, ctx.target(), ctx);
    }

    @Override
    public @NotNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
