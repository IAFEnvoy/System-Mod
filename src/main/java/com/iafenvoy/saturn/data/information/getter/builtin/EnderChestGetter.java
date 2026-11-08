package com.iafenvoy.saturn.data.information.getter.builtin;

import net.minecraft.nbt.Tag;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public enum EnderChestGetter implements EntityValueGetter<Map<String, PlayerViewItem>> {
    INSTANCE;

    public static final MapCodec<EnderChestGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EnderChestGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Map<String, PlayerViewItem>> valueCodec() {
        return Codec.unboundedMap(Codec.STRING, PlayerViewItem.CODEC);
    }

    @Override
    public ViewValue<Map<String, PlayerViewItem>> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known(GetterSupport.container(player, player.getEnderChestInventory(), player.getEnderChestInventory().getContainerSize()), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<Map<String, PlayerViewItem>> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "EnderItems", data -> GetterSupport.savedItems(data.getList("EnderItems", Tag.TAG_COMPOUND), context));
    }
}
