package com.iafenvoy.system.module.builtin.jobs;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.OnlineJobsInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class OnlineJobsModule extends AbstractAgentModule<OnlineJobsInput> {
    public OnlineJobsModule(ToolRegistry.Tool<OnlineJobsInput, OnlineJobsOutput> query) {
        super(OnlineJobsInput.CODEC, "Browse online gathering/production jobs and current progress.", List.of(query), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<OnlineJobsInput> routing() {
        return ModuleRouting.<OnlineJobsInput>defaults().button(a -> new OnlineJobsInput(a.number(0, "offset")))
                .command(AgentCommand.literal(new OnlineJobsInput(0), "jobs", "作业"))
                .semantic(RoutingProfile.read(_ -> new OnlineJobsInput(0), List.of("查看挂机作业", "当前采集进度", "有哪些在线生产项目"),
                        List.of("Show online jobs", "Check my production progress", "List available gathering jobs"))
                        .choice("system.action.job_refresh"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, OnlineJobsInput input) throws Exception {
        return AgentResult.data("system.result.online_jobs", context.tool(BuiltinTools.ONLINE_JOBS, "jobs", input));
    }
}
