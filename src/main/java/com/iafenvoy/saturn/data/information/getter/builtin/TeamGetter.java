package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum TeamGetter implements EntityValueGetter<String> {
    INSTANCE;

    public static final MapCodec<TeamGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<TeamGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public ViewValue<String> capture(Entity entity) {
        return ViewValue.known(entity.getTeam() == null ? "" : entity.getTeam().getName(), ValueOrigin.CAPTURED);
    }
}
