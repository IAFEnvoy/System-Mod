package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.PlayerViewData;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

public record EffectGetter(ResourceLocation effect, Value value) implements EntityValueGetter<Integer> {
    public static final MapCodec<EffectGetter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("effect").forGetter(EffectGetter::effect),
            Value.CODEC.optionalFieldOf("value", Value.AMPLIFIER).forGetter(EffectGetter::value)
    ).apply(i, EffectGetter::new));

    @Override
    public MapCodec<EffectGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return Codec.INT;
    }

    @Override
    public ViewValue<Integer> capture(Entity entity) {
        return this.select(EffectsGetter.INSTANCE.capture(entity));
    }

    @Override
    public ViewValue<Integer> readSaved(SavedPlayerViewContext context) {
        return this.select(EffectsGetter.INSTANCE.readSaved(context));
    }

    @Override
    public ViewValue<Integer> fallback(PlayerView view) {
        return this.select(view.read(EffectsGetter.INSTANCE));
    }

    @Override
    public List<EntityValueGetter<?>> dependencies() {
        return List.of(EffectsGetter.INSTANCE);
    }

    private ViewValue<Integer> select(ViewValue<List<PlayerViewData.Effect>> effects) {
        return GetterSupport.select(effects, entry -> entry.id().equals(this.effect), entry -> Optional.of(switch (this.value) {
            case AMPLIFIER -> entry.amplifier();
            case DURATION -> entry.duration();
        }));
    }

    public enum Value {
        AMPLIFIER, DURATION;
        public static final Codec<Value> CODEC = ExtraEnumCodecs.enumCodec(Value.class);
    }
}
