package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public enum SelectedSlotGetter implements EntityValueGetter<Integer> {
    INSTANCE;

    public static final MapCodec<SelectedSlotGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<SelectedSlotGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return Codec.intRange(0, 8);
    }

    @Override
    public ViewValue<Integer> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known(player.getInventory().selected, ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<Integer> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "SelectedItemSlot", data -> data.getInt("SelectedItemSlot"));
    }
}
