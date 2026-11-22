package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SpaceTakeModule extends AbstractAgentModule<SpaceTakeInput> {
    public SpaceTakeModule(ToolRegistry.Tool<SpaceTakeInput, SpaceOutput, ExecutionContext> take) {
        super(SpaceTakeInput.CODEC, "Withdraw existing content from my system space by observed entry ID or exact name. One by default; stack=true takes one native stack. Never create items or invent entry IDs.",
                List.of(take), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<SpaceTakeInput> routing() {
        return ModuleRouting.<SpaceTakeInput>defaults()
                .button(a -> new SpaceTakeInput(a.required("entry"), "", a.number(1, "amount"), a.flag("stack"), true))
                .command(AgentCommand.argument("<quantity and name>", text -> {
                    SpaceTakeInput input = SpaceTakeInput.parse("取出" + text);
                    if (input == null) throw new AgentException(AgentFailure.INVALID_INPUT);
                    return input;
                }, "取出", "拿出", "提取"))
                .command(AgentCommand.argument("<amount and name>", text -> {
                    SpaceTakeInput input = SpaceTakeInput.parse("take " + text);
                    if (input == null) throw new AgentException(AgentFailure.INVALID_INPUT);
                    return input;
                }, "take", "withdraw"))
                .explicit(SpaceTakeInput::parse)
                .semantic(RoutingProfile.hints(List.of("从系统空间取出一组铁锭", "取出十个钻石"),
                        List.of("Take a stack of iron ingots from system space", "Withdraw 10 diamonds")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, SpaceTakeInput input) throws Exception {
        AgentResult<CardUpdate> result = SystemResults.data("system.result.inbox", context.tool(BuiltinTools.SPACE_TAKE, "take", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
