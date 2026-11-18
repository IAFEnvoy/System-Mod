package com.iafenvoy.system.module.builtin.collection;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.CollectionRegisterInput;
import com.iafenvoy.system.data.output.CollectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class CollectionRegisterModule extends AbstractAgentModule<CollectionRegisterInput> {
    public CollectionRegisterModule(ToolRegistry.Tool<CollectionRegisterInput, CollectionOutput> tool) {
        super(CollectionRegisterInput.CODEC, "Register matching items from the player's current inventory into an observed collection; this never consumes items.", List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<CollectionRegisterInput> routing() {
        return ModuleRouting.<CollectionRegisterInput>defaults()
                .button(a -> new CollectionRegisterInput(a.identifier("collection")))
                .command(AgentCommand.identifier("collection", CollectionRegisterInput::new, "collection_register", "登记收藏"))
                .explicitOnly().semantic(RoutingProfile.hints(List.of("登记物品收藏"), List.of("Register collection items")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, CollectionRegisterInput input) throws Exception {
        return AgentResult.data("system.collection.registered", context.tool(BuiltinTools.COLLECTION_REGISTER, "register", input));
    }
}
