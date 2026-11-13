package com.iafenvoy.system.assistant.tool.economy;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.data.input.BalanceInput;
import com.iafenvoy.system.data.output.WalletOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record BalanceTool(Wallets wallets) implements ToolRegistry.Handler<BalanceInput, WalletOutput> {
    public ToolRegistry.Tool<BalanceInput, WalletOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.BALANCE, false, true, false, this)
                .forReasoning("Read only the current player's currency balances. This cannot mint, transfer or spend money.");
    }

    @Override
    public CompletionStage<WalletOutput> execute(ExecutionContext context, BalanceInput input) {
        return CompletableFuture.completedFuture(this.wallets.query(context));
    }
}
