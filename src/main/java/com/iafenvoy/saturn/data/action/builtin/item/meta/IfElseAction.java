package com.iafenvoy.saturn.data.action.builtin.item.meta;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record IfElseAction(ItemCondition condition, ItemAction ifAction,
                           ItemAction elseAction) implements ItemAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            ItemAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            ItemAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        if (this.condition.test(holder, stack, ctx)) this.ifAction.execute(holder, stack, ctx);
        else this.elseAction.execute(holder, stack, ctx);
    }

    @Override
    public @NotNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
