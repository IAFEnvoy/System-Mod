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

public enum EffectsGetter implements EntityValueGetter<List<PlayerViewData.Effect>> {
    INSTANCE;

    public static final MapCodec<EffectsGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EffectsGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<List<PlayerViewData.Effect>> valueCodec() {
        return PlayerViewData.Effect.CODEC.listOf();
    }

    @Override
    public ViewValue<List<PlayerViewData.Effect>> capture(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return ViewValue.missing(MissingReason.UNSUPPORTED);
        return ViewValue.known(living.getActiveEffects().stream().map(effect -> new PlayerViewData.Effect(
                BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()), effect.getAmplifier(), effect.getDuration(),
                effect.isAmbient(), effect.isVisible())).toList(), ValueOrigin.CAPTURED);
    }

    @Override
    public ViewValue<List<PlayerViewData.Effect>> readSaved(SavedPlayerViewContext context) {
        return GetterSupport.saved(context.player(), "active_effects", data -> {
            List<PlayerViewData.Effect> effects = new ArrayList<>();
            for (Tag raw : data.getList("active_effects", Tag.TAG_COMPOUND)) {
                if (!(raw instanceof CompoundTag effect)) throw new IllegalArgumentException("Invalid saved effect");
                effects.add(new PlayerViewData.Effect(ResourceLocation.parse(effect.getString("id")),
                        effect.getInt("amplifier"), effect.getInt("duration"),
                        effect.getBoolean("ambient"), effect.getBoolean("show_particles")));
            }
            return effects;
        });
    }
}
