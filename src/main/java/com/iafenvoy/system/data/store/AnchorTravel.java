package com.iafenvoy.system.data.store;

import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Last travel attempt. An interrupted payment/travel is never retried automatically.
 */
public record AnchorTravel(String id, TeleportAnchor destination, CommerceCatalog.Price price, State state) {
    public static final Codec<AnchorTravel> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(AnchorTravel::id),
            TeleportAnchor.CODEC.fieldOf("destination").forGetter(AnchorTravel::destination),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(AnchorTravel::price),
            State.CODEC.fieldOf("state").forGetter(AnchorTravel::state)
    ).apply(i, AnchorTravel::new));

    public boolean blocked() {
        return this.state != State.COMPLETED;
    }

    public AnchorTravel state(State value) {
        return new AnchorTravel(this.id, this.destination, this.price, value);
    }

    public enum State implements StringRepresentable {
        PREPARED, UNKNOWN, COMPLETED;
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
