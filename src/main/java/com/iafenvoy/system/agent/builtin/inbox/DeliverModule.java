package com.iafenvoy.system.agent.builtin.inbox;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.output.StateOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;

/**
 * Delivers one existing entitlement through the game adapter.
 */
public final class DeliverModule extends BuiltinModule<EntitlementInput> {
    public DeliverModule(ToolRegistry.Tool<EntitlementInput, StateOutput> deliver) {
        super(ResultTypes.DELIVER_ACTION, "Deliver an existing reward entitlement obtained from the inbox; never invent entitlement IDs.",
                EntitlementInput.CODEC, List.of(deliver), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<EntitlementInput> routing() {
        return ModuleRouting.<EntitlementInput>defaults()
                .semantic(RoutingProfile.hints(
                        List.of("把收件箱的东西发给我", "领取我的奖励物品", "把补给放进背包", "给我发放已入账奖励"),
                        List.of("Deliver my inbox reward", "Put my supplies in my inventory", "Claim my reward items", "Give me the credited supplies")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, EntitlementInput input) throws Exception {
        return AgentResult.data("system.result.delivery", ResultTypes.DELIVERY_STATE.of(context.tool(BuiltinTools.DELIVER, "deliver", input)));
    }
}
