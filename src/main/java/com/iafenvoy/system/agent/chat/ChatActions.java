package com.iafenvoy.system.agent.chat;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.react.ReActTool;
import com.iafenvoy.system.agent.routing.ModuleRouting;

import java.util.ArrayList;
import java.util.List;

/**
 * Free chat advertises existing workflows, so module enablement and confirmation rules have one owner.
 */
public final class ChatActions {
    private ChatActions() {
    }

    public static List<ReActTool<?, AgentResult>> bind(ExecutionContext context) {
        List<ReActTool<?, AgentResult>> tools = new ArrayList<>();
        for (AgentModule<?> module : context.chatActions()) tools.add(bind(context, module));
        return List.copyOf(tools);
    }

    private static <I extends AgentAction> ReActTool<I, AgentResult> bind(ExecutionContext context, AgentModule<I> module) {
        AgentModule.Descriptor descriptor = module.descriptor();
        String description = descriptor.description();
        if (module.routing().selection() == ModuleRouting.Selection.EXPLICIT_ONLY)
            description += " Only use when the current user message clearly and explicitly requests this capability.";
        SchemaMapCodec<I> codec = module.inputCodec() instanceof SchemaMapCodec<I> schema
                ? schema : new SchemaMapCodec<>(module.inputCodec(), descriptor.schema());
        return new ReActTool<>(descriptor.id().toString(), description, codec, context::action);
    }
}
