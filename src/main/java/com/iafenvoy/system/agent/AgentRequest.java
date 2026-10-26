package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.util.AgentCodecs;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** Typed requests inside the application; the existing packet shape is encoded only at its boundary. */
public sealed interface AgentRequest {
    String mode();

    default String text() { return ""; }

    default String encodedInput() { return "{}"; }

    record Chat(String text) implements AgentRequest {
        public String mode() { return ""; }
    }

    record Invoke(TypedPayload<?> call, String text) implements AgentRequest {
        public String mode() { return this.call.type().id().toString(); }
        public String encodedInput() { return this.call.json().toString(); }
    }

    enum Control implements AgentRequest {
        STATE("state"), HISTORY("history"), FORGET("forget"), OPEN("open");
        private final String mode;
        Control(String mode) { this.mode = mode; }
        public String mode() { return this.mode; }
    }

    record Preferences(boolean history, boolean external) implements AgentRequest {
        public static final Codec<Preferences> CODEC = AgentCodecs.strict(RecordCodecBuilder.<Preferences>mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("history").forGetter(Preferences::history),
                Codec.BOOL.fieldOf("external").forGetter(Preferences::external)
        ).apply(i, Preferences::new))).codec();
        public String mode() { return "preferences"; }
        public String encodedInput() { return CodecIO.write(CODEC, this); }
    }

    static AgentRequest decode(String mode, String input, String text) {
        if (mode.isEmpty()) {
            CodecIO.parse(EmptyInput.CODEC.codec(), input, 4096);
            return new Chat(text);
        }
        if (mode.equals("preferences")) return CodecIO.parse(Preferences.CODEC, input, 4096);
        for (Control control : Control.values()) if (mode.equals(control.mode())) {
            CodecIO.parse(EmptyInput.CODEC.codec(), input, 4096);
            return control;
        }
        Identifier id = Identifier.tryParse(mode);
        if (id == null) throw new AgentException("INVALID_INPUT");
        return new Invoke(ResultTypes.ACTIONS.parse(id, input, 4096), text);
    }
}
