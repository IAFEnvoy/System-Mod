package com.iafenvoy.saturn.data.action.builtin.bientity.meta;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SequenceBiEntityAction(List<BiEntityAction> actions) implements BiEntityAction {
    public static final MapCodec<SequenceBiEntityAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBiEntityAction::new, SequenceBiEntityAction::actions);

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.actor(), ctx.target(), ctx));
    }

    @Override
    public @NotNull MapCodec<SequenceBiEntityAction> codec() {
        return CODEC;
    }
}
