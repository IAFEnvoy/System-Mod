package com.iafenvoy.system.agent.react;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A typed, loop-local binding owns argument validation and projects its tool result into an observation.
 */
public record ReActTool<I, O>(String id, String description, SchemaMapCodec<I> inputCodec,
                              Function<I, String> repetitionKey, Handler<I, O> handler) {
    public ReActTool {
        Objects.requireNonNull(id);
        Objects.requireNonNull(description);
        Objects.requireNonNull(inputCodec);
        Objects.requireNonNull(repetitionKey);
        Objects.requireNonNull(handler);
        if (id.equals("answer")) throw new IllegalArgumentException("Reserved ReAct tool ID");
    }

    public ReActTool(String id, String description, SchemaMapCodec<I> inputCodec, Handler<I, O> handler) {
        this(id, description, inputCodec, input -> CodecIO.write(inputCodec.codec(), input), handler);
    }

    public Description describe() {
        return new Description(this.description, this.inputCodec.schema());
    }

    public record Description(String description, JsonElement input) {
        public static final Codec<Description> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("description").forGetter(Description::description),
                ExtraCodecs.JSON.fieldOf("input").forGetter(Description::input)
        ).apply(i, Description::new));
        public static final Codec<Map<String, Description>> CATALOGUE = Codec.unboundedMap(Codec.STRING, CODEC);
    }

    @FunctionalInterface
    public interface Handler<I, O> {
        O execute(I input) throws Exception;
    }
}
