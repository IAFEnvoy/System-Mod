package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.PlayerViewItem;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public enum CarriedItemGetter implements EntityValueGetter<Map<String, PlayerViewItem>> {
    INSTANCE;

    public static final MapCodec<CarriedItemGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<CarriedItemGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Map<String, PlayerViewItem>> valueCodec() {
        return Codec.unboundedMap(Codec.STRING, PlayerViewItem.CODEC);
    }

    @Override
    public ViewValue<Map<String, PlayerViewItem>> capture(Entity entity) {
        if (!(entity instanceof Player player)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        ItemStack carried = player.containerMenu.getCarried();
        return ViewValue.known(carried.isEmpty() ? Map.of() : Map.of("cursor", PlayerViewItem.capture(carried, player.registryAccess())), ValueOrigin.CAPTURED);
    }
}
