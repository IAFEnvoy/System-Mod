package com.iafenvoy.saturn.data.action.builtin.bientity.meta;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.BiEntityActionContext;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Applies an entity action to the target of a bi-entity interaction. Anything the nested action places (a bolt, an
 * explosion, particles) happens at the activation's own place by default - a talisman fires from where the carrier
 * stands - so {@code use_target_position} is what "strike this one where it stands" needs.
 */
public record TargetAction(EntityAction action, boolean useTargetPosition) implements BiEntityAction {
    public static final MapCodec<TargetAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.CODEC.fieldOf("action").forGetter(TargetAction::action),
            Codec.BOOL.optionalFieldOf("use_target_position", false).forGetter(TargetAction::useTargetPosition)
    ).apply(i, TargetAction::new));

    @Override
    public void execute(@NotNull BiEntityActionContext ctx) {
        // No origin on the nested context: the place falls back to the entity it is about, which is the target.
        if (this.useTargetPosition) {
            this.action.execute(new EntityActionContext(ctx.target(), ctx.formula()));
            return;
        }
        this.action.execute(ctx.target(), ctx);
    }

    @Override
    public @NotNull MapCodec<TargetAction> codec() {
        return CODEC;
    }
}
