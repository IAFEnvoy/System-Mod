package com.iafenvoy.system.module.builtin.collection;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.CollectionQueryInput;
import com.iafenvoy.system.data.output.CollectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class CollectionQueryModule extends AbstractAgentModule<CollectionQueryInput> {
    public CollectionQueryModule(ToolRegistry.Tool<CollectionQueryInput, CollectionOutput, ExecutionContext> tool) {
        super(CollectionQueryInput.CODEC, "List the server-defined item collection compendium and registered entries without consuming items.", List.of(tool), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<CollectionQueryInput> routing() {
        return ModuleRouting.<CollectionQueryInput>defaults()
                .button(a -> new CollectionQueryInput(a.text("collection"), a.number(0, "offset")))
                .command(AgentCommand.argument("[collection]", value -> new CollectionQueryInput(value, 0), "collection", "图鉴", "收藏"))
                .semantic(RoutingProfile.read(_ -> new CollectionQueryInput("", 0), List.of("查看收藏图鉴", "我的物品收藏"), List.of("Show my collection compendium", "My item collection")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, CollectionQueryInput input) throws Exception {
        return SystemResults.data("system.collection.title", context.tool(BuiltinTools.COLLECTION_QUERY, "collection", input));
    }
}
