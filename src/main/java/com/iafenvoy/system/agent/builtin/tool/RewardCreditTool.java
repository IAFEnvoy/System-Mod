package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.RewardCreditInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.store.PlayerData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Credits a trusted event entitlement without overwriting an existing delivery.
 */
public final class RewardCreditTool implements ToolRegistry.Handler<RewardCreditInput, CreditReceiptOutput> {
    public ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.REWARD_CREDIT, true, true, true, this);
    }

    @Override
    public CompletionStage<CreditReceiptOutput> execute(ExecutionContext context, RewardCreditInput input) {
        String id = input.entitlementId();
        PlayerData.Reward reward = input.reward();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            if (data.inbox.size() >= 4096 && !data.inbox.containsKey(id)) throw new AgentException("STORAGE_FULL");
            data.inbox.putIfAbsent(id, new PlayerData.Delivery(id, reward));
            return new CreditReceiptOutput(id, data.inbox.get(id).reward, data.inbox.get(id).state);
        }));
    }
}
