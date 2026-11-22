package com.iafenvoy.system.module.builtin.progress;

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
import com.iafenvoy.system.data.input.AchievementQueryInput;
import com.iafenvoy.system.data.output.AchievementsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class AchievementQueryModule extends AbstractAgentModule<AchievementQueryInput> {
    public AchievementQueryModule(ToolRegistry.Tool<AchievementQueryInput, AchievementsOutput, ExecutionContext> query) {
        super(AchievementQueryInput.CODEC, "View achievement series, incomplete goals, trusted progress and earned rewards. Read-only; never creates progress.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<AchievementQueryInput> routing() {
        return ModuleRouting.<AchievementQueryInput>defaults().button(a -> new AchievementQueryInput(a.number(0, "offset")))
                .command(AgentCommand.literal(new AchievementQueryInput(0), "achievements", "成就", "成就总览"))
                .semantic(RoutingProfile.hints(List.of("查看成就总览", "我的成就进度"), List.of("Show my achievements", "View achievement progress")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, AchievementQueryInput input) throws Exception {
        return SystemResults.data("system.ui.nav_achievements", context.tool(BuiltinTools.ACHIEVEMENTS, "query", input));
    }
}
