package com.iafenvoy.saturn.data.action.builtin.item.meta;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SequenceItemAction(List<ItemAction> actions) implements ItemAction {
    public static final MapCodec<SequenceItemAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceItemAction::new, SequenceItemAction::actions);

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.holder(), ctx.stack(), ctx));
    }

    @Override
    public @NotNull MapCodec<SequenceItemAction> codec() {
        return CODEC;
    }
}
