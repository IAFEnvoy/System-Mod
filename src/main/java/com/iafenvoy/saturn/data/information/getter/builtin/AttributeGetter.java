package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.PlayerViewData;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Selects an attribute by its stable id, independently of list order.
 */
public record AttributeGetter(ResourceLocation attribute, Value value) implements EntityValueGetter<Double> {
    public static final MapCodec<AttributeGetter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("attribute").forGetter(AttributeGetter::attribute),
            Value.CODEC.optionalFieldOf("value", Value.EFFECTIVE).forGetter(AttributeGetter::value)
    ).apply(i, AttributeGetter::new));

    @Override
    public MapCodec<AttributeGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Double> valueCodec() {
        return MiscCodecs.FINITE;
    }

    @Override
    public ViewValue<Double> capture(Entity entity) {
        return this.select(AttributesGetter.INSTANCE.capture(entity));
    }

    @Override
    public ViewValue<Double> readSaved(SavedPlayerViewContext context) {
        return this.select(AttributesGetter.INSTANCE.readSaved(context));
    }

    @Override
    public ViewValue<Double> fallback(PlayerView view) {
        return this.select(view.read(AttributesGetter.INSTANCE));
    }

    @Override
    public List<EntityValueGetter<?>> dependencies() {
        return List.of(AttributesGetter.INSTANCE);
    }

    private ViewValue<Double> select(ViewValue<List<PlayerViewData.Attribute>> attributes) {
        return GetterSupport.select(attributes, entry -> entry.id().equals(this.attribute), entry -> switch (this.value) {
            case BASE -> Optional.of(entry.base());
            case EFFECTIVE -> entry.effective();
        });
    }

    public enum Value {
        BASE, EFFECTIVE;
        public static final Codec<Value> CODEC = ExtraEnumCodecs.enumCodec(Value.class);
    }
}
