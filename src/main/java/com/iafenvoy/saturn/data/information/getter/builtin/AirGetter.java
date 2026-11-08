package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum AirGetter implements EntityValueGetter<Integer> {
    INSTANCE;

    public static final MapCodec<AirGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<AirGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return Codec.INT;
    }

    @Override
    public ViewValue<Integer> capture(Entity entity) {
        return ViewValue.known(entity.getAirSupply(), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<Integer> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "Air", data -> data.getInt("Air"));
    }
}
