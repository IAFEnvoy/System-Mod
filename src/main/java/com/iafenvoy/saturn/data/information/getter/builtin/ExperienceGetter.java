package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public enum ExperienceGetter implements EntityValueGetter<PlayerViewData.Experience> {
    INSTANCE;

    public static final MapCodec<ExperienceGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<ExperienceGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<PlayerViewData.Experience> valueCodec() {
        return PlayerViewData.Experience.CODEC;
    }

    @Override
    public ViewValue<PlayerViewData.Experience> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known(new PlayerViewData.Experience(player.experienceLevel, player.totalExperience, player.experienceProgress), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<PlayerViewData.Experience> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "XpLevel", data -> new PlayerViewData.Experience(
                data.getInt("XpLevel"), data.getInt("XpTotal"), data.getDouble("XpP")));
    }
}
