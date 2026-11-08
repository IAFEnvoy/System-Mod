package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public enum AbsorptionGetter implements EntityValueGetter<Double> {
    INSTANCE;

    public static final MapCodec<AbsorptionGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<AbsorptionGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Double> valueCodec() {
        return MiscCodecs.FINITE;
    }

    @Override
    public ViewValue<Double> capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known((double) living.getAbsorptionAmount(), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<Double> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "AbsorptionAmount", data -> data.getDouble("AbsorptionAmount"));
    }
}
