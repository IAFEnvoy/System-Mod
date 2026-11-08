package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public enum AdvancementsGetter implements EntityValueGetter<String> {
    INSTANCE;

    public static final MapCodec<AdvancementsGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<AdvancementsGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public ViewValue<String> readSaved(SavedPlayerViewContext context) {
        return context.advancements().map(value -> ViewValue.known(value, ValueOrigin.SAVED))
                .orElseGet(() -> ViewValue.missing(MissingReason.NOT_CAPTURED));
    }
}
