package com.iafenvoy.saturn.data.action.builtin.item;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record DamageItemAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<DamageItemAction> CODEC = NumberProvider.CODEC.fieldOf("amount").xmap(DamageItemAction::new, DamageItemAction::amount);

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        ItemStack stack = ctx.stack();
        if (!stack.isDamageableItem()) return;
        double amount = MiscUtil.positive(this.amount.evaluate(ctx.formula()));
        if (amount <= 0.0D) return;
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + Math.max(1, (int) Math.round(amount))));
    }

    @Override
    public @NotNull MapCodec<DamageItemAction> codec() {
        return CODEC;
    }
}
