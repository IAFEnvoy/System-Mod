package com.iafenvoy.system.agent.react;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.tool.ToolPriority;
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
                              Function<I, String> repetitionKey, Handler<I, O> handler, ToolPriority priority) {
    public ReActTool {
        Objects.requireNonNull(id);
        Objects.requireNonNull(description);
        Objects.requireNonNull(inputCodec);
        Objects.requireNonNull(repetitionKey);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(priority);
        if (id.equals("answer")) throw new IllegalArgumentException("Reserved ReAct tool ID");
    }

    public ReActTool(String id, String description, SchemaMapCodec<I> inputCodec, Handler<I, O> handler) {
        this(id, description, inputCodec, input -> CodecIO.write(inputCodec.codec(), input), handler);
    }

    public ReActTool(String id, String description, SchemaMapCodec<I> inputCodec, Function<I, String> repetitionKey, Handler<I, O> handler) {
        this(id, description, inputCodec, repetitionKey, handler, ToolPriority.LOCAL);
    }

    public ReActTool<I, O> withPriority(ToolPriority priority) {
        return new ReActTool<>(this.id, this.description, this.inputCodec, this.repetitionKey, this.handler, priority);
    }

    public Description describe() {
        return new Description(this.description, this.inputCodec.schema(), this.priority);
    }

    public record Description(String description, JsonElement input, ToolPriority priority) {
        public static final Codec<Description> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("description").forGetter(Description::description),
                ExtraCodecs.JSON.fieldOf("input").forGetter(Description::input),
                ToolPriority.CODEC.fieldOf("priority").forGetter(Description::priority)
        ).apply(i, Description::new));
        public static final Codec<Map<String, Description>> CATALOGUE = Codec.unboundedMap(Codec.STRING, CODEC);
    }

    @FunctionalInterface
    public interface Handler<I, O> {
        O execute(I input) throws Exception;
    }
}
