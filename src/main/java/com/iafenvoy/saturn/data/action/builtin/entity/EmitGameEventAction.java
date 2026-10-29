package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;

public record EmitGameEventAction(Holder<GameEvent> event) implements EntityAction {
    public static final MapCodec<EmitGameEventAction> CODEC = GameEvent.CODEC.fieldOf("event").xmap(EmitGameEventAction::new, EmitGameEventAction::event);

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        ctx.entity().gameEvent(this.event);
    }

    @Override
    public @NotNull MapCodec<EmitGameEventAction> codec() {
        return CODEC;
    }
}
