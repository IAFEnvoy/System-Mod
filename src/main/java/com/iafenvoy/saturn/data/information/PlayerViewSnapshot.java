package com.iafenvoy.saturn.data.information;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.api.playerview.PlayerViewWriter;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.getter.EntityValueKey;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.PlayerViewMeta;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;

import java.util.*;
import java.util.function.Predicate;

/**
 * Detached query results. Unknown payloads and getter definitions survive without their owning mod.
 */
public final class PlayerViewSnapshot implements PlayerView {
    private static final Codec<Query> QUERY_CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityValueKey.CODEC.fieldOf("getter").forGetter(Query::getter),
            Codec.PASSTHROUGH.fieldOf("data").forGetter(Query::data)
    ).apply(i, Query::new));
    private static final Codec<Envelope> ENVELOPE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1).fieldOf("version").forGetter(Envelope::version),
            PlayerViewMeta.CODEC.fieldOf("meta").forGetter(Envelope::meta),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.PASSTHROUGH).fieldOf("fields").forGetter(Envelope::fields),
            QUERY_CODEC.listOf().optionalFieldOf("queries", List.of()).forGetter(Envelope::queries)
    ).apply(i, Envelope::new));
    public static final Codec<PlayerViewSnapshot> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<PlayerViewSnapshot, T>> decode(DynamicOps<T> ops, T input) {
            return ENVELOPE_CODEC.decode(ops, input).map(pair -> {
                Envelope envelope = pair.getFirst();
                Map<ResourceLocation, Tag> fields = new LinkedHashMap<>();
                envelope.fields.forEach((id, value) -> fields.put(id, value.convert(NbtOps.INSTANCE).getValue()));
                Map<EntityValueKey, Tag> queries = new LinkedHashMap<>();
                envelope.queries.forEach(query -> queries.put(query.getter, query.data.convert(NbtOps.INSTANCE).getValue()));
                return Pair.of(new PlayerViewSnapshot(envelope.meta, fields, queries, nbtOps(ops)), pair.getSecond());
            });
        }

        @Override
        public <T> DataResult<T> encode(PlayerViewSnapshot input, DynamicOps<T> ops, T prefix) {
            Map<ResourceLocation, Dynamic<?>> fields = new LinkedHashMap<>();
            input.fields.forEach((id, value) -> fields.put(id, new Dynamic<>(NbtOps.INSTANCE, value.copy())));
            List<Query> queries = input.queries.entrySet().stream()
                    .map(entry -> new Query(entry.getKey(), new Dynamic<>(NbtOps.INSTANCE, entry.getValue().copy()))).toList();
            return ENVELOPE_CODEC.encode(new Envelope(1, input.meta, fields, queries), ops, prefix);
        }
    };
    private final PlayerViewMeta meta;
    private final Map<ResourceLocation, Tag> fields;
    private final Map<EntityValueKey, Tag> queries;
    private final DynamicOps<Tag> ops;

    private PlayerViewSnapshot(PlayerViewMeta meta, Map<ResourceLocation, Tag> fields, Map<EntityValueKey, Tag> queries, DynamicOps<Tag> ops) {
        this.meta = Objects.requireNonNull(meta);
        this.fields = copy(fields);
        this.queries = copy(queries);
        this.ops = Objects.requireNonNull(ops);
    }

    private static <K> Map<K, Tag> copy(Map<K, Tag> values) {
        Map<K, Tag> copy = new LinkedHashMap<>();
        values.forEach((id, value) -> copy.put(id, value.copy()));
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public PlayerViewMeta meta() {
        return this.meta;
    }

    @Override
    public DynamicOps<JsonElement> serializationOps() {
        return this.ops instanceof RegistryOps<?> registries ? registries.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE;
    }

    @Override
    public <T> ViewValue<T> read(EntityValueGetter<T> getter) {
        try {
            EntityValueKey key = EntityValueKey.of(getter, this.serializationOps());
            Tag raw = key.isUnit() ? this.fields.get(key.type().orElseThrow()) : this.queries.get(key);
            // A recorded missing/invalid entry is authoritative; only absent queries may project broader facts.
            if (raw == null) return Objects.requireNonNull(getter.fallback(this));
            if (!(raw instanceof CompoundTag envelope)) return ViewValue.missing(MissingReason.INVALID);
            if (envelope.getInt("schema") != getter.schemaVersion())
                return ViewValue.missing(MissingReason.UNSUPPORTED);
            Optional<State> state = State.CODEC.parse(this.ops, envelope.get("state")).result();
            if (state.orElse(null) == State.MISSING)
                return MissingReason.CODEC.parse(this.ops, envelope.get("reason")).result()
                        .map(ViewValue::<T>missing).orElseGet(() -> ViewValue.missing(MissingReason.INVALID));
            if (state.orElse(null) != State.KNOWN || !envelope.contains("value"))
                return ViewValue.missing(MissingReason.INVALID);
            Optional<ValueOrigin> origin = ValueOrigin.CODEC.parse(this.ops, envelope.get("origin")).result();
            return origin.map(source -> getter.valueCodec().parse(this.ops, envelope.get("value").copy()).result()
                            .map(value -> ViewValue.known(value, source)).orElseGet(() -> ViewValue.missing(MissingReason.INVALID)))
                    .orElseGet(() -> ViewValue.missing(MissingReason.INVALID));
        } catch (RuntimeException ignored) {
            return ViewValue.missing(MissingReason.INVALID);
        }
    }

    public Set<ResourceLocation> getterIds() {
        Set<ResourceLocation> result = new LinkedHashSet<>(this.fields.keySet());
        this.queries.keySet().forEach(key -> key.type().ifPresent(result::add));
        return Collections.unmodifiableSet(result);
    }

    /**
     * Filters actual payloads, including unknown query types, before the host sends them.
     */
    public PlayerViewSnapshot filter(Predicate<ResourceLocation> allowed) {
        Map<ResourceLocation, Tag> fields = new LinkedHashMap<>();
        this.fields.forEach((id, value) -> {
            if (allowed.test(id)) fields.put(id, value);
        });
        Map<EntityValueKey, Tag> queries = new LinkedHashMap<>();
        this.queries.forEach((key, value) -> {
            if (key.type().filter(allowed).isPresent()) queries.put(key, value);
        });
        return new PlayerViewSnapshot(this.meta, fields, queries, this.ops);
    }

    public static Builder builder(PlayerViewMeta meta, DynamicOps<?> ops) {
        return new Builder(meta, nbtOps(ops));
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this.meta, this.ops);
        this.fields.forEach((id, value) -> builder.fields.put(id, value.copy()));
        this.queries.forEach((key, value) -> builder.queries.put(key, value.copy()));
        return builder;
    }

    private static DynamicOps<Tag> nbtOps(DynamicOps<?> ops) {
        return ops instanceof RegistryOps<?> registries ? registries.withParent(NbtOps.INSTANCE) : NbtOps.INSTANCE;
    }

    private record Query(EntityValueKey getter, Dynamic<?> data) {
    }

    private record Envelope(int version, PlayerViewMeta meta, Map<ResourceLocation, Dynamic<?>> fields, List<Query> queries) {
    }

    private enum State {
        KNOWN, MISSING;
        private static final Codec<State> CODEC = ExtraEnumCodecs.enumCodec(State.class);
    }

    public static final class Builder implements PlayerViewWriter {
        private final PlayerViewMeta meta;
        private final DynamicOps<Tag> ops;
        private final Map<ResourceLocation, Tag> fields = new LinkedHashMap<>();
        private final Map<EntityValueKey, Tag> queries = new LinkedHashMap<>();

        private Builder(PlayerViewMeta meta, DynamicOps<Tag> ops) {
            this.meta = Objects.requireNonNull(meta);
            this.ops = ops;
        }

        private EntityValueKey key(EntityValueGetter<?> getter) {
            DynamicOps<JsonElement> jsonOps = this.ops instanceof RegistryOps<?> registries
                    ? registries.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE;
            return EntityValueKey.of(getter, jsonOps);
        }

        @Override
        public <T> void put(EntityValueGetter<T> getter, T value, ValueOrigin origin) {
            EntityValueKey key = this.key(getter);
            CompoundTag envelope = envelope(getter, State.KNOWN);
            envelope.put("value", getter.valueCodec().encodeStart(this.ops, Objects.requireNonNull(value)).getOrThrow().copy());
            envelope.put("origin", ValueOrigin.CODEC.encodeStart(this.ops, origin).getOrThrow());
            this.store(key, envelope);
        }

        @Override
        public void missing(EntityValueGetter<?> getter, MissingReason reason) {
            EntityValueKey key = this.key(getter);
            CompoundTag envelope = envelope(getter, State.MISSING);
            envelope.put("reason", MissingReason.CODEC.encodeStart(this.ops, reason).getOrThrow());
            this.store(key, envelope);
        }

        private void store(EntityValueKey key, CompoundTag envelope) {
            if (key.isUnit()) this.fields.put(key.type().orElseThrow(), envelope);
            else this.queries.put(key, envelope);
        }

        @Override
        public boolean contains(EntityValueGetter<?> getter) {
            EntityValueKey key = this.key(getter);
            return key.isUnit() ? this.fields.containsKey(key.type().orElseThrow()) : this.queries.containsKey(key);
        }

        public PlayerViewSnapshot build() {
            return new PlayerViewSnapshot(this.meta, this.fields, this.queries, this.ops);
        }

        private static CompoundTag envelope(EntityValueGetter<?> getter, State state) {
            CompoundTag envelope = new CompoundTag();
            envelope.putInt("schema", getter.schemaVersion());
            envelope.put("state", State.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
            return envelope;
        }
    }
}
