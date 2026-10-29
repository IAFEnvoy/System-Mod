package com.iafenvoy.saturn.data.action.builtin.item;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Takes health from the acting entity as ordinary magic damage, so resistances and invulnerability apply.
 * It never checks whether the holder can afford it, so a claim can kill the holder who just made it.
 */
public record ConsumeHealthItemAction(NumberProvider amount) implements ItemAction {
    public static final MapCodec<ConsumeHealthItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(ConsumeHealthItemAction::amount)
    ).apply(i, ConsumeHealthItemAction::new));

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        // Server only, and it belongs to a living holder: a stack with no actor has nobody to charge.
        if (!(ctx.holder() instanceof LivingEntity holder)) return;
        if (!(holder.level() instanceof ServerLevel level)) return;
        double amount = MiscUtil.positive(this.amount.evaluate(ctx.formula()));
        if (amount <= 0.0D) return;
        // 1.21.1 applies damage through hurt() on both sides; the guard above already restricts this to servers.
        holder.hurt(holder.damageSources().magic(), (float) amount);
    }

    @Override
    public @NotNull MapCodec<ConsumeHealthItemAction> codec() {
        return CODEC;
    }
}
