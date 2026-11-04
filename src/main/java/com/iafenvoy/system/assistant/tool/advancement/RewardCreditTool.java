package com.iafenvoy.system.assistant.tool.advancement;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.data.input.RewardCreditInput;
import com.iafenvoy.system.data.output.CreditReceiptOutput;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import com.iafenvoy.system.module.builtin.storage.InboxDataStorage;
import com.iafenvoy.system.util.PersistHolder;

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
        PersistHolder<Reward> reward = input.reward();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            InboxDataStorage storage = ModuleData.get(data, InboxSystemModule.STORAGE);
            storage.assertDeliveryCount(id);
            storage.putDeliveryIfAbsent(id, new Delivery(id, reward));
            Delivery delivery = storage.getDelivery(id);
            return new CreditReceiptOutput(id, delivery.reward, delivery.state);
        }));
    }
}
