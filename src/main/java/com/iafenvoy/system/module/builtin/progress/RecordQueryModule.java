package com.iafenvoy.system.module.builtin.progress;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.RecordQueryInput;
import com.iafenvoy.system.data.output.RecordRewardsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class RecordQueryModule extends AbstractAgentModule<RecordQueryInput> {
    public RecordQueryModule(ToolRegistry.Tool<RecordQueryInput, RecordRewardsOutput> query) {
        super(RecordQueryInput.CODEC, "Read durable progress counters and earned milestone or quest-point rewards. Never creates progress or grants new rewards.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<RecordQueryInput> routing() {
        return ModuleRouting.<RecordQueryInput>defaults().button(a -> new RecordQueryInput(a.number(0, "offset")))
                .command(AgentCommand.literal(new RecordQueryInput(0), "records", "里程碑", "进度奖励"))
                .semantic(RoutingProfile.read(_ -> new RecordQueryInput(0), List.of("查看里程碑奖励", "查看任务积分奖励", "我的进度记录"), List.of("Show earned milestone rewards", "My progress records", "Claimable quest points")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, RecordQueryInput input) throws Exception {
        return AgentResult.data("system.records.title", context.tool(BuiltinTools.RECORD_QUERY, "records", input));
    }
}
