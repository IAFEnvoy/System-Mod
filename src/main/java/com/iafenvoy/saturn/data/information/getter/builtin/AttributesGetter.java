package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum AttributesGetter implements EntityValueGetter<List<PlayerViewData.Attribute>> {
    INSTANCE;

    public static final MapCodec<AttributesGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<AttributesGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<List<PlayerViewData.Attribute>> valueCodec() {
        return PlayerViewData.Attribute.CODEC.listOf();
    }

    @Override
    public ViewValue<List<PlayerViewData.Attribute>> capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known(BuiltInRegistries.ATTRIBUTE.holders().filter(living.getAttributes()::hasAttribute)
                .map(attribute -> new PlayerViewData.Attribute(attribute.key().location(), living.getAttributeBaseValue(attribute),
                        Optional.of(living.getAttributeValue(attribute)))).toList(), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<List<PlayerViewData.Attribute>> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "attributes", data -> {
            List<PlayerViewData.Attribute> attributes = new ArrayList<>();
            for (Tag raw : data.getList("attributes", Tag.TAG_COMPOUND)) {
                if (!(raw instanceof CompoundTag attribute))
                    throw new IllegalArgumentException("Invalid saved attribute");
                attributes.add(new PlayerViewData.Attribute(ResourceLocation.parse(attribute.getString("id")),
                        attribute.getDouble("base"), Optional.empty()));
            }
            return attributes;
        });
    }
}
