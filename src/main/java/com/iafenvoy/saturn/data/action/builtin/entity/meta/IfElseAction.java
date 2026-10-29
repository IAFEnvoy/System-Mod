package com.iafenvoy.saturn.data.action.builtin.entity.meta;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record IfElseAction(EntityCondition condition, EntityAction ifAction,
                           EntityAction elseAction) implements EntityAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            EntityAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            EntityAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (this.condition.test(entity, ctx)) this.ifAction.execute(entity, ctx);
        else this.elseAction.execute(entity, ctx);
    }

    @Override
    public @NotNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
