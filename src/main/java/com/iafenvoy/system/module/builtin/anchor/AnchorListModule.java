package com.iafenvoy.system.module.builtin.anchor;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.AnchorListInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class AnchorListModule extends AbstractAgentModule<AnchorListInput> {
    public AnchorListModule(ToolRegistry.Tool<AnchorListInput, AnchorListOutput> query) {
        super(AnchorListInput.CODEC, "List my personal saved teleport anchors, exact IDs, unlocked capacity and cooldown without teleporting.",
                List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<AnchorListInput> routing() {
        return ModuleRouting.<AnchorListInput>defaults().button(_ -> AnchorListInput.INSTANCE)
                .command(AgentCommand.literal(AnchorListInput.INSTANCE, "anchors", "锚点", "传送锚点列表"))
                .semantic(RoutingProfile.read(_ -> AnchorListInput.INSTANCE,
                        List.of("查看传送锚点", "我的锚点列表", "还有几个锚点位置"),
                        List.of("Show my teleport anchors", "List saved waypoints", "How many anchor slots do I have?")))
                .shortcut("system.ui.nav_anchor", AnchorListInput.INSTANCE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, AnchorListInput input) throws Exception {
        return AgentResult.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_LIST, "anchors", input));
    }
}
