package com.iafenvoy.system.data.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Immutable inventory debit receipt; the inventory itself is owned by Minecraft's player file.
 */
public record SpaceDeposit(String id, ItemStackTemplate item, int slot, int amount, State state, long created,
                           String reviewer, String reason) {
    public static final Codec<SpaceDeposit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(SpaceDeposit::id),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(SpaceDeposit::item),
            Codec.intRange(0, 35).fieldOf("slot").forGetter(SpaceDeposit::slot),
            Codec.intRange(1, 2304).fieldOf("amount").forGetter(SpaceDeposit::amount),
            State.CODEC.fieldOf("state").forGetter(SpaceDeposit::state),
            Codec.LONG.fieldOf("created").forGetter(SpaceDeposit::created),
            Codec.STRING.optionalFieldOf("reviewer", "").forGetter(SpaceDeposit::reviewer),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(SpaceDeposit::reason)
    ).apply(i, SpaceDeposit::new));

    public enum State implements StringRepresentable {
        PREPARED, EXECUTING, COMPLETED, FAILED, UNKNOWN;
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public SpaceDeposit withState(State state) {
        return new SpaceDeposit(this.id, this.item, this.slot, this.amount, state, this.created, this.reviewer, this.reason);
    }

    public boolean reserved() {
        return this.state == State.PREPARED || this.state == State.EXECUTING || this.state == State.UNKNOWN;
    }

    public String entry() {
        return "deposit:" + this.id;
    }
}
