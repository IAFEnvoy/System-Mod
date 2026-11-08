package com.iafenvoy.system.agent.chat;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.react.ReActTool;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolContext;
import com.iafenvoy.system.agent.tool.ToolObservation;
import com.iafenvoy.system.agent.tool.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Free chat combines enabled workflows and a scoped read catalogue; neither path bypasses its original authority.
 */
public final class ChatActions {
    private ChatActions() {
    }

    public static List<ReActTool<?, ChatObservation>> bind(ExecutionContext context, ToolContext reads) {
        Map<String, ReActTool<?, ChatObservation>> tools = new LinkedHashMap<>();
        for (AgentModule<?> module : context.chatActions()) {
            ReActTool<?, ChatObservation> tool = bind(context, module);
            tools.put(tool.id(), tool);
        }
        // A provider cannot replace an existing workflow by exporting a tool with the same ID.
        for (ToolRegistry.Tool<?, ?> read : reads.tools()) tools.putIfAbsent(read.id(), bind(reads, read));
        return List.copyOf(tools.values());
    }

    private static <I extends AgentAction> ReActTool<I, ChatObservation> bind(ExecutionContext context, AgentModule<I> module) {
        AgentModule.Descriptor descriptor = module.descriptor();
        String description = descriptor.description();
        if (module.routing().selection() == ModuleRouting.Selection.EXPLICIT_ONLY)
            description += " Only use when the current user message clearly and explicitly requests this capability.";
        SchemaMapCodec<I> codec = module.inputCodec() instanceof SchemaMapCodec<I> schema
                ? schema : new SchemaMapCodec<>(module.inputCodec(), descriptor.schema());
        return new ReActTool<I, ChatObservation>(descriptor.id().toString(), description, codec,
                input -> new ChatObservation.Workflow(context.action(input))).withPriority(module.routing().priority());
    }

    private static <I, O> ReActTool<I, ChatObservation> bind(ToolContext context, ToolRegistry.Tool<I, O> tool) {
        SchemaMapCodec<I> codec = (SchemaMapCodec<I>) tool.type().inputCodec();
        String description = tool.reasoningDescription().isBlank() ? "Read using registered tool " + tool.id() : tool.reasoningDescription();
        return new ReActTool<I, ChatObservation>(tool.id(), description, codec,
                input -> new ChatObservation.Tool(new ToolObservation<>(tool.type(), input, context.call(tool.type(), input))))
                .withPriority(tool.priority());
    }
}
