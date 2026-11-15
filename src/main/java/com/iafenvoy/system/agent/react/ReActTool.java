package com.iafenvoy.system.agent.react;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.tool.ToolPriority;
import com.iafenvoy.system.util.CodecIO;

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

    public ModelClient.Function function(String name) {
        return new ModelClient.Function(name, "Capability: " + this.id + ". Priority: " + this.priority.getSerializedName()
                + ". " + this.description, this.inputCodec.schema());
    }

    @FunctionalInterface
    public interface Handler<I, O> {
        O execute(I input) throws Exception;
    }
}
