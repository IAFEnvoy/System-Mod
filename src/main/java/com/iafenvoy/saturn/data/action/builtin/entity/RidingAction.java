package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.BiEntityAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Executes nested actions for a matching vehicle, optionally through the full riding chain.
 */
public record RidingAction(EntityAction action, BiEntityAction biEntityAction,
                           BiEntityCondition biEntityCondition, boolean recursive) implements EntityAction {
    public static final MapCodec<RidingAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.optionalCodec("action").forGetter(RidingAction::action),
            BiEntityAction.optionalCodec("bientity_action").forGetter(RidingAction::biEntityAction),
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(RidingAction::biEntityCondition),
            Codec.BOOL.optionalFieldOf("recursive", false).forGetter(RidingAction::recursive)
    ).apply(i, RidingAction::new));

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        Entity vehicle = entity.getVehicle();
        while (vehicle != null) {
            boolean matches = this.biEntityCondition.test(entity, vehicle, ctx);
            if (matches) {
                this.action.execute(vehicle, ctx);
                this.biEntityAction.execute(entity, vehicle, ctx);
            }
            if (!this.recursive) return;
            vehicle = vehicle.getVehicle();
        }
    }

    @Override
    public @NotNull MapCodec<RidingAction> codec() {
        return CODEC;
    }
}
