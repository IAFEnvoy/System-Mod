package com.iafenvoy.saturn.data.information.getter.builtin;

import net.minecraft.nbt.Tag;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.PlayerViewData;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public enum LocationGetter implements EntityValueGetter<PlayerViewData.Location> {
    INSTANCE;

    public static final MapCodec<LocationGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<LocationGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<PlayerViewData.Location> valueCodec() {
        return PlayerViewData.Location.CODEC;
    }

    @Override
    public ViewValue<PlayerViewData.Location> capture(Entity entity) {
        return ViewValue.known(new PlayerViewData.Location(entity.level().dimension().location(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getYRot(), entity.getXRot()), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<PlayerViewData.Location> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "Dimension", data -> {
            ListTag pos = data.getList("Pos", Tag.TAG_DOUBLE);
            ListTag rot = data.getList("Rotation", Tag.TAG_FLOAT);
            if (pos.size() != 3 || rot.size() != 2) throw new IllegalArgumentException("Invalid saved location");
            return new PlayerViewData.Location(ResourceLocation.parse(data.getString("Dimension")),
                    pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
                    rot.getDouble(0), rot.getDouble(1));
        });
    }
}
