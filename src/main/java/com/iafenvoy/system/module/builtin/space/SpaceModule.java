package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.SpaceInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.ArrayList;
import java.util.List;

/**
 * The space is independent of sign-in, commerce and the kinds of content stored in it.
 */
public final class SpaceModule extends AbstractAgentModule<SpaceInput> {
    private final CommerceTransactions commerce;

    public SpaceModule(ToolRegistry.Tool<SpaceInput, SpaceOutput, ExecutionContext> query, CommerceTransactions commerce) {
        super(SpaceInput.CODEC, "View my system space and stored contents, quantities and withdrawal actions.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
        this.commerce = commerce;
    }

    @Override
    public ModuleRouting<SpaceInput> routing() {
        return ModuleRouting.<SpaceInput>defaults()
                .button(_ -> SpaceInput.INSTANCE)
                .command(AgentCommand.literal(SpaceInput.INSTANCE, "space", "系统空间", "inbox", "收件箱", "系统背包"))
                .semantic(RoutingProfile.read(_ -> SpaceInput.INSTANCE,
                        List.of("打开系统空间", "查看系统空间", "查看我的系统背包", "查看我的收件箱", "现在有哪些待领取奖励"),
                        List.of("Open system space", "Show my system storage", "Show my reward inbox", "What rewards are waiting for me?")))
                .shortcut("system.action.view_inbox", SpaceInput.INSTANCE);
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, SpaceInput input) throws Exception {
        List<CardUpdate> cards = new ArrayList<>();
        cards.add(CardUpdate.show(context.tool(BuiltinTools.SPACE_QUERY, "space", input)));
        // Unsettled purchases remain visible without exposing their locked contents as withdrawable entries.
        cards.addAll(this.commerce.pending(context).stream().map(CardUpdate::show).toList());
        return SystemResults.presented(AgentResult.Status.SUCCEEDED, "system.result.inbox", "", cards);
    }
}
