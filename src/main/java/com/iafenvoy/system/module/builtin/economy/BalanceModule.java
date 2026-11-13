package com.iafenvoy.system.module.builtin.economy;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.BalanceInput;
import com.iafenvoy.system.data.output.WalletOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class BalanceModule extends AbstractAgentModule<BalanceInput> {
    public BalanceModule(ToolRegistry.Tool<BalanceInput, WalletOutput> balance) {
        super(BalanceInput.CODEC, "Check the player's wallet and all defined currencies.", List.of(balance), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<BalanceInput> routing() {
        return ModuleRouting.<BalanceInput>defaults().button(_ -> BalanceInput.INSTANCE)
                .command(AgentCommand.literal(BalanceInput.INSTANCE, "balance", "wallet", "余额", "钱包"))
                .semantic(RoutingProfile.read(_ -> BalanceInput.INSTANCE,
                        List.of("查看我的系统币余额", "我还有多少系统币", "打开我的钱包"),
                        List.of("Check my balance", "How many system coins do I have", "Show my wallet"))
                        .choice("system.ui.nav_economy"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, BalanceInput input) throws Exception {
        return AgentResult.data("system.result.balance", context.tool(BuiltinTools.BALANCE, "balance", input));
    }
}
