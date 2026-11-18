package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-owned public destination. Clients can only select its registry ID.
 */
public record TeleportDestination(Component label, Component description, ResourceKey<Level> dimension, Vec3 position,
                                  float yaw, float pitch, List<Cost> costs, int order, boolean enabled,
                                  EntryConditions conditions) implements ConditionalEntry {
    public static final Codec<TeleportDestination> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(TeleportDestination::label),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(TeleportDestination::description),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TeleportDestination::dimension),
            Vec3.CODEC.validate(value -> value.isFinite() ? DataResult.success(value) : DataResult.error(() -> "Destination position must be finite"))
                    .fieldOf("position").forGetter(TeleportDestination::position),
            Codec.FLOAT.validate(value -> Float.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Destination yaw must be finite"))
                    .optionalFieldOf("yaw", 0F).forGetter(TeleportDestination::yaw),
            Codec.floatRange(-90, 90).optionalFieldOf("pitch", 0F).forGetter(TeleportDestination::pitch),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(TeleportDestination::costs),
            Codec.INT.optionalFieldOf("order", 0).forGetter(TeleportDestination::order),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(TeleportDestination::enabled),
            EntryConditions.CODEC.forGetter(TeleportDestination::conditions)
    ).apply(i, TeleportDestination::new));

    public TeleportDestination {
        costs = List.copyOf(costs);
    }

    public TeleportAnchor anchor(Identifier id) {
        return new TeleportAnchor("public:" + id, this.label.getString(), this.dimension, this.position, this.yaw, this.pitch);
    }
}
