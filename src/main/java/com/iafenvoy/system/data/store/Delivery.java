package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public final class Delivery {
    public static final Codec<Delivery> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
            PersistHolder.codec(Reward.SNAPSHOT_CODEC).optionalFieldOf("reward").forGetter(v -> Optional.ofNullable(v.reward)),
            Codec.STRING.optionalFieldOf("state", "PENDING").forGetter(v -> v.state),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created),
            Codec.BOOL.optionalFieldOf("stored", false).forGetter(v -> v.stored)
    ).apply(i, Delivery::new));
    public String id;
    public PersistHolder<Reward> reward;
    public String state = "PENDING";
    public long created;
    public boolean stored;

    public Delivery() {
    }

    private Delivery(Optional<String> id, Optional<PersistHolder<Reward>> reward, String state, long created, boolean stored) {
        this.id = id.orElse(null);
        this.reward = reward.orElse(null);
        this.state = state;
        this.created = created;
        this.stored = stored;
    }

    public Delivery(String id, PersistHolder<Reward> reward) {
        this.id = id;
        this.reward = reward;
        this.created = System.currentTimeMillis();
    }

    public Delivery copy() {
        Delivery copy = new Delivery();
        copy.id = this.id;
        copy.reward = this.reward;
        copy.state = this.state;
        copy.created = this.created;
        copy.stored = this.stored;
        return copy;
    }
}
