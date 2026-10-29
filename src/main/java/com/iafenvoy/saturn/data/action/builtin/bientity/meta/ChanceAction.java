package com.iafenvoy.saturn.data.action.builtin.bientity.meta;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record ChanceAction(BiEntityAction action, float chance,
                           BiEntityAction failAction) implements BiEntityAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            BiEntityAction.optionalCodec("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        if (actor.getRandom().nextFloat() < this.chance) this.action.execute(actor, target, ctx);
        else this.failAction.execute(actor, target, ctx);
    }

    @Override
    public @NotNull MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
