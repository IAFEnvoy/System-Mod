package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.NotNull;

public record GamemodeCondition(GameType gamemode) implements EntityCondition {
    public static final MapCodec<GamemodeCondition> CODEC = GameType.CODEC.fieldOf("gamemode").xmap(GamemodeCondition::new, GamemodeCondition::gamemode);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == this.gamemode;
    }

    @Override
    public @NotNull MapCodec<GamemodeCondition> codec() {
        return CODEC;
    }
}
