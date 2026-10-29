package com.iafenvoy.saturn.data.action.builtin.item.meta;

import com.iafenvoy.saturn.data.Weighted;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ChoiceAction(List<Weighted<ItemAction>> actions) implements ItemAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(ItemAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        Weighted<ItemAction> entry = Weighted.select(this.actions, holder.getRandom());
        if (entry != null) entry.value().execute(holder, ctx.stack(), ctx);
    }

    @Override
    public @NotNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
