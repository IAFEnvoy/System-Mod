package com.iafenvoy.saturn.data.information.getter;

import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

/**
 * A configurable, read-only query. The registry stores its parameter codec; snapshots store its result.
 */
public interface EntityValueGetter<T> {
    Codec<EntityValueGetter<?>> CODEC = SaturnRegistries.ENTITY_VALUE_GETTER.byNameCodec().dispatch("type", EntityValueGetter::codec, Function.identity());

    MapCodec<? extends EntityValueGetter<T>> codec();

    Codec<T> valueCodec();

    default int schemaVersion() {
        return 1;
    }

    default ResourceLocation type() {
        ResourceLocation id = SaturnRegistries.ENTITY_VALUE_GETTER.getKey(this.codec());
        if (id == null) throw new IllegalArgumentException("Unregistered entity value getter codec");
        return id;
    }

    /**
     * Runs on the supplied entity's owning thread. Never creates attachments or advances gameplay state.
     */
    default ViewValue<T> capture(Entity entity) {
        return ViewValue.missing(MissingReason.UNSUPPORTED);
    }

    /**
     * Only reads supplied saved data. NOT_CAPTURED leaves the snapshot entry absent for host supplementation.
     */
    default ViewValue<T> readSaved(SavedPlayerViewContext context) {
        return ViewValue.missing(MissingReason.NOT_CAPTURED);
    }

    /**
     * Optional projection of facts already present in a detached view. Never resolves a live entity.
     */
    default ViewValue<T> fallback(PlayerView view) {
        return ViewValue.missing(MissingReason.NOT_CAPTURED);
    }

    /**
     * Broader queries needed by fallback, included in the capture plan and permission provenance.
     */
    default List<EntityValueGetter<?>> dependencies() {
        return List.of();
    }
}
