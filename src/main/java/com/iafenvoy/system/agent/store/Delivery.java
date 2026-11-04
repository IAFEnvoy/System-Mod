package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public final class Delivery {
    public static final Codec<Delivery> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
            Reward.CODEC.optionalFieldOf("reward").forGetter(v -> Optional.ofNullable(v.reward)),
            Codec.STRING.optionalFieldOf("state", "PENDING").forGetter(v -> v.state),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created)
    ).apply(i, Delivery::new));
    public String id;
    public Reward reward;
    public String state = "PENDING";
    public long created;

    public Delivery() {
    }

    private Delivery(Optional<String> id, Optional<Reward> reward, String state, long created) {
        this.id = id.orElse(null);
        this.reward = reward.orElse(null);
        this.state = state;
        this.created = created;
    }

    public Delivery(String id, Reward reward) {
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
        return copy;
    }
}
