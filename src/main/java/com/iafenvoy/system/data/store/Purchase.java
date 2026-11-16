package com.iafenvoy.system.data.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public final class Purchase {
    public static final Codec<Purchase> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id").forGetter(v -> Optional.ofNullable(v.id)),
            CommerceOrder.CODEC.fieldOf("order").forGetter(v -> v.order),
            Codec.STRING.optionalFieldOf("state", "PREPARED").forGetter(v -> v.state),
            Codec.STRING.optionalFieldOf("error", "").forGetter(v -> v.error),
            Codec.LONG.optionalFieldOf("created", 0L).forGetter(v -> v.created),
            Codec.STRING.optionalFieldOf("review", "").forGetter(v -> v.review),
            Codec.STRING.optionalFieldOf("reviewer", "").forGetter(v -> v.reviewer),
            Codec.LONG.optionalFieldOf("reviewed", 0L).forGetter(v -> v.reviewed)
    ).apply(i, Purchase::new));
    public String id;
    public CommerceOrder order;
    public String state = "PREPARED", error = "";
    public long created;
    public String review = "", reviewer = "";
    public long reviewed;

    public Purchase() {
    }

    private Purchase(Optional<String> id, CommerceOrder order, String state, String error, long created, String review, String reviewer, long reviewed) {
        this.id = id.orElse(null);
        this.order = order;
        this.state = state;
        this.error = error;
        this.created = created;
        this.review = review;
        this.reviewer = reviewer;
        this.reviewed = reviewed;
    }

    public Purchase copy() {
        return new Purchase(Optional.ofNullable(this.id), this.order, this.state, this.error, this.created, this.review, this.reviewer, this.reviewed);
    }
}
