package com.iafenvoy.system.data.store;

import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/** Immutable durable terms; order identity is the original quote identity. */
public record SalvageOrder(String id, Terms terms, State state, long created, String error,
                           String reviewer, String reason, long reviewed) {
    public static final Codec<SalvageOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(SalvageOrder::id),
            Terms.CODEC.fieldOf("terms").forGetter(SalvageOrder::terms),
            State.CODEC.fieldOf("state").forGetter(SalvageOrder::state),
            Codec.LONG.fieldOf("created").forGetter(SalvageOrder::created),
            Codec.STRING.optionalFieldOf("error", "").forGetter(SalvageOrder::error),
            Codec.STRING.optionalFieldOf("reviewer", "").forGetter(SalvageOrder::reviewer),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(SalvageOrder::reason),
            Codec.LONG.optionalFieldOf("reviewed", 0L).forGetter(SalvageOrder::reviewed)
    ).apply(i, SalvageOrder::new));

    public record Terms(Identifier owner, Identifier rule, long incarnation, String revision, ItemStackTemplate input,
                        CommerceCatalog.Price price, Reward reward) {
        public static final Codec<Terms> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("owner").forGetter(Terms::owner),
                Identifier.CODEC.fieldOf("rule").forGetter(Terms::rule),
                Codec.LONG.fieldOf("incarnation").forGetter(Terms::incarnation),
                Codec.STRING.fieldOf("revision").forGetter(Terms::revision),
                ItemStackTemplate.CODEC.fieldOf("input").forGetter(Terms::input),
                CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(Terms::price),
                Reward.SNAPSHOT_CODEC.codec().fieldOf("reward").forGetter(Terms::reward)
        ).apply(i, Terms::new));
    }

    public enum State implements StringRepresentable {
        PREPARED, EXECUTING, COMPLETED, FAILED, UNKNOWN;
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public SalvageOrder withState(State state, String error) {
        return new SalvageOrder(this.id, this.terms, state, this.created, error, this.reviewer, this.reason, this.reviewed);
    }

    public SalvageOrder review(boolean consumed, String reviewer, String reason) {
        return new SalvageOrder(this.id, this.terms, consumed ? State.COMPLETED : State.FAILED, this.created,
                consumed ? "" : "NOT_CONSUMED", reviewer, reason, System.currentTimeMillis());
    }

    public String entitlement() {
        return "salvage:" + this.id;
    }
}
