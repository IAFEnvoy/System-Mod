package com.iafenvoy.saturn.data.information;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetters;
import com.iafenvoy.saturn.data.information.getter.EntityValueKey;
import com.iafenvoy.saturn.data.information.model.*;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.function.Supplier;

/**
 * Parses supplied entities and saved data into detached values; never loads, stores or sends player files.
 */
public final class PlayerViews {
    private PlayerViews() {
    }

    public static PlayerViewSnapshot parse(Entity player) {
        long now = System.currentTimeMillis();
        return parse(player, new PlayerViewMeta(PlayerViewRef.current(player.getUUID()), UUID.randomUUID(),
                PlayerViewMeta.SourceKind.LIVE, Optional.of(now), Optional.of(player.level().getGameTime()), now));
    }

    public static PlayerViewSnapshot parse(Entity player, PlayerViewMeta meta) {
        return parse(player, meta, List.of());
    }

    /**
     * Extra parameterized queries supplement defaults and the getters referenced by enabled information rows.
     */
    public static PlayerViewSnapshot parse(Entity entity, PlayerViewMeta meta, Collection<? extends EntityValueGetter<?>> requested) {
        if (entity.level() instanceof ServerLevel level && !level.getServer().isSameThread())
            throw new IllegalStateException("Parse a server entity on the server thread");
        if (!meta.ref().accountId().equals(entity.getUUID()))
            throw new IllegalArgumentException("Entity view identity mismatch");
        RegistryAccess registries = entity.registryAccess();
        PlayerViewSnapshot.Builder builder = PlayerViewSnapshot.builder(meta, registries.createSerializationContext(NbtOps.INSTANCE));
        for (EntityValueGetter<?> getter : plan(registries, requested)) capture(builder, getter, entity);
        return builder.build();
    }

    public static PlayerViewSnapshot parse(SavedPlayerViewContext context) {
        return parse(context, Optional.empty());
    }

    public static PlayerViewSnapshot parse(SavedPlayerViewContext context, Optional<PlayerViewSnapshot> recorded) {
        return parse(context, recorded, List.of());
    }

    /**
     * Existing entries, including explicit missing states, always take precedence over saved-data fallback.
     */
    public static PlayerViewSnapshot parse(SavedPlayerViewContext context, Optional<PlayerViewSnapshot> recorded,
                                           Collection<? extends EntityValueGetter<?>> requested) {
        recorded.ifPresent(view -> {
            if (!view.meta().ref().equals(context.meta().ref()))
                throw new IllegalArgumentException("Recorded player view reference mismatch");
        });
        PlayerViewSnapshot.Builder builder = recorded.map(PlayerViewSnapshot::toBuilder)
                .orElseGet(() -> PlayerViewSnapshot.builder(context.meta(), context.registries().createSerializationContext(NbtOps.INSTANCE)));
        for (EntityValueGetter<?> getter : plan(context.registries(), requested))
            if (!builder.contains(getter)) readSaved(builder, getter, context);
        return builder.build();
    }

    private static List<EntityValueGetter<?>> plan(HolderLookup.Provider registries, Collection<? extends EntityValueGetter<?>> requested) {
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Map<EntityValueKey, EntityValueGetter<?>> result = new LinkedHashMap<>();
        EntityValueGetters.defaults(ops).forEach(getter -> add(result, getter, ops, 0));
        requested.forEach(getter -> add(result, getter, ops, 0));
        registries.lookup(SaturnResourceKeys.INFORMATION_ROW).ifPresent(rows -> rows.listElements().forEach(row -> {
            if (row.value().enabled()) row.value().getters().forEach(getter -> add(result, getter, ops, 0));
        }));
        return List.copyOf(result.values());
    }

    private static void add(Map<EntityValueKey, EntityValueGetter<?>> queries, EntityValueGetter<?> getter, DynamicOps<JsonElement> ops, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Entity getter dependencies are too deep");
        if (queries.putIfAbsent(EntityValueKey.of(getter, ops), getter) != null) return;
        getter.dependencies().forEach(dependency -> add(queries, dependency, ops, depth + 1));
    }

    private static <T> void capture(PlayerViewSnapshot.Builder builder, EntityValueGetter<T> getter, Entity entity) {
        parseValue(builder, getter, () -> getter.capture(entity), false);
    }

    private static <T> void readSaved(PlayerViewSnapshot.Builder builder, EntityValueGetter<T> getter, SavedPlayerViewContext context) {
        parseValue(builder, getter, () -> getter.readSaved(context), true);
    }

    private static <T> void parseValue(PlayerViewSnapshot.Builder builder, EntityValueGetter<T> getter, Supplier<ViewValue<T>> parse, boolean saved) {
        try {
            ViewValue<T> value = Objects.requireNonNull(parse.get(), "Entity value getter returned null");
            if (value.isKnown()) builder.put(getter, value.value().orElseThrow(), value.origin());
            else if (!saved || value.missingReason().orElseThrow() != MissingReason.NOT_CAPTURED)
                builder.missing(getter, value.missingReason().orElseThrow());
        } catch (RuntimeException failure) {
            builder.missing(getter, MissingReason.INVALID);
            Saturn.LOGGER.warn("Failed to parse entity value getter {}", getter.type(), failure);
        }
    }
}
