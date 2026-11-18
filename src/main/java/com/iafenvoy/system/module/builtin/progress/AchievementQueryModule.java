package com.iafenvoy.system.module.builtin.progress;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.AchievementQueryInput;
import com.iafenvoy.system.data.output.AchievementsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class AchievementQueryModule extends AbstractAgentModule<AchievementQueryInput> {
    public AchievementQueryModule(ToolRegistry.Tool<AchievementQueryInput, AchievementsOutput> query) {
        super(AchievementQueryInput.CODEC, "View achievement series, incomplete goals, trusted progress and earned rewards. Read-only; never creates progress.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<AchievementQueryInput> routing() {
        return ModuleRouting.<AchievementQueryInput>defaults().button(a -> new AchievementQueryInput(a.number(0, "offset")))
                .command(AgentCommand.literal(new AchievementQueryInput(0), "achievements", "成就", "成就总览"))
                .semantic(RoutingProfile.hints(List.of("查看成就总览", "我的成就进度"), List.of("Show my achievements", "View achievement progress")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, AchievementQueryInput input) throws Exception {
        return AgentResult.data("system.ui.nav_achievements", context.tool(BuiltinTools.ACHIEVEMENTS, "query", input));
    }
}
