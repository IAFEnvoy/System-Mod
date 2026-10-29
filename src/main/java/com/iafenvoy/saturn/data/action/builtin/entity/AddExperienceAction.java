package com.iafenvoy.saturn.data.action.builtin.entity;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.util.codec.OptionalCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

/**
 * Grants vanilla experience points and/or levels, to players only.
 */
public record AddExperienceAction(OptionalInt points, OptionalInt levels) implements EntityAction {
    public static final MapCodec<AddExperienceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            OptionalCodecs.intCodec("points").forGetter(AddExperienceAction::points),
            OptionalCodecs.intCodec("levels").forGetter(AddExperienceAction::levels)
    ).apply(i, AddExperienceAction::new));

    @Override
    public void execute(@NotNull EntityActionContext ctx) {
        if (ctx.entity() instanceof Player player) {
            this.points.ifPresent(player::giveExperiencePoints);
            this.levels.ifPresent(player::giveExperienceLevels);
        }
    }

    @Override
    public @NotNull MapCodec<AddExperienceAction> codec() {
        return CODEC;
    }
}
