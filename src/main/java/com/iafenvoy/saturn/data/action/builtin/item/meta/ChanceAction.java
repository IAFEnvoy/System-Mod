package com.iafenvoy.saturn.data.action.builtin.item.meta;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record ChanceAction(ItemAction action, float chance, ItemAction failAction) implements ItemAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            ItemAction.optionalCodec("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        if (holder.getRandom().nextFloat() < this.chance) this.action.execute(holder, stack, ctx);
        else this.failAction.execute(holder, stack, ctx);
    }

    @Override
    public @NotNull MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
