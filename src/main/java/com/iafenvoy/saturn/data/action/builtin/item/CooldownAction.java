package com.iafenvoy.saturn.data.action.builtin.item;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record CooldownAction(int ticks) implements ItemAction {
    public static final MapCodec<CooldownAction> CODEC = Codec.INT.fieldOf("ticks").xmap(CooldownAction::new, CooldownAction::ticks);

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        ItemStack stack = ctx.stack();
        if (ctx.holder() instanceof Player player && !stack.isEmpty() && this.ticks > 0)
            player.getCooldowns().addCooldown(stack.getItem(), this.ticks);
    }

    @Override
    public @NotNull MapCodec<CooldownAction> codec() {
        return CODEC;
    }
}
