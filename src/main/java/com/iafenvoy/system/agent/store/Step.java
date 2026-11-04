package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public final class Step {
    public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
            State.CODEC.optionalFieldOf("state", State.RUNNING).forGetter(v -> v.state),
            Codec.STRING.optionalFieldOf("fingerprint").forGetter(v -> Optional.ofNullable(v.fingerprint)),
            Codec.PASSTHROUGH.optionalFieldOf("result").forGetter(v -> Optional.ofNullable(v.result))
    ).apply(i, Step::new));
    public State state = State.RUNNING;
    public String fingerprint;
    public Dynamic<?> result;

    public Step() {
    }

    private Step(State state, Optional<String> fingerprint, Optional<Dynamic<?>> result) {
        this.state = state;
        this.fingerprint = fingerprint.orElse(null);
        this.result = result.orElse(null);
    }

    public Step copy() {
        Dynamic<?> resultCopy = this.result == null ? null : new Dynamic<>(JsonOps.INSTANCE, this.result.convert(JsonOps.INSTANCE).getValue().deepCopy());
        return new Step(this.state, Optional.ofNullable(this.fingerprint), Optional.ofNullable(resultCopy));
    }
}
