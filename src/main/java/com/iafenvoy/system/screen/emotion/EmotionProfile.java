package com.iafenvoy.system.screen.emotion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * Resource packs own state names, speech frames and event/timeout transitions.
 */
public record EmotionProfile(String defaultState, Map<String, State> states, Map<String, String> transitions) {
    public static final Codec<EmotionProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("default_state").forGetter(EmotionProfile::defaultState),
            Codec.unboundedMap(Codec.STRING, State.CODEC).fieldOf("states").forGetter(EmotionProfile::states),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).lenientOptionalFieldOf("transitions", Map.of()).forGetter(EmotionProfile::transitions)
    ).apply(i, EmotionProfile::new));

    public EmotionProfile {
        states = Map.copyOf(states);
        transitions = Map.copyOf(transitions);
    }

    public record State(EmotionFrame texture, List<EmotionFrame> speaking, int frameTime, int duration,
                        String next, Map<String, String> transitions) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                EmotionFrame.CODEC.fieldOf("texture").forGetter(State::texture),
                EmotionFrame.CODEC.listOf().lenientOptionalFieldOf("speaking", List.of()).forGetter(State::speaking),
                Codec.INT.lenientOptionalFieldOf("frame_time", 180).forGetter(State::frameTime),
                Codec.INT.lenientOptionalFieldOf("duration", 0).forGetter(State::duration),
                Codec.STRING.lenientOptionalFieldOf("next", "").forGetter(State::next),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).lenientOptionalFieldOf("transitions", Map.of()).forGetter(State::transitions)
        ).apply(i, State::new));

        public State {
            speaking = List.copyOf(speaking);
            transitions = Map.copyOf(transitions);
        }
    }
}
