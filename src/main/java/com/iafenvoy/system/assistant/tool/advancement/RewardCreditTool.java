package com.iafenvoy.system.assistant.tool.advancement;

import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.input.RewardCreditInput;
import com.iafenvoy.system.data.output.CreditReceiptOutput;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Credits a trusted event entitlement without overwriting an existing delivery.
 */
public final class RewardCreditTool implements ToolRegistry.Handler<RewardCreditInput, CreditReceiptOutput, ExecutionContext> {
    private final CommerceCatalogs catalogs;

    public RewardCreditTool(CommerceCatalogs catalogs) {
        this.catalogs = catalogs;
    }

    public ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput, ExecutionContext> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.REWARD_CREDIT, true, true, true, this);
    }

    @Override
    public CompletionStage<CreditReceiptOutput> execute(ExecutionContext context, RewardCreditInput input) {
        String id = input.entitlementId();
        context.ensureMemoryCurrent();
        Delivery existing = context.store().read(context.player(), data -> ModuleData.get(data, SpaceSystemModule.STORAGE).getDelivery(id));
        if (existing != null)
            return CompletableFuture.completedFuture(new CreditReceiptOutput(id, existing.reward, existing.state));
        // Only new entitlements require the entry's current conditions; existing credits remain claimable.
        PersistHolder<Reward> reward = this.catalogs.requireReward(context, input.rewardId(), true).value().reward(input.rewardId());
        context.ensureMemoryCurrent();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            context.check();
            SpaceDataStorage storage = ModuleData.get(data, SpaceSystemModule.STORAGE);
            storage.assertDeliveryCount(id);
            storage.putDeliveryIfAbsent(id, new Delivery(id, reward));
            Delivery delivery = storage.getDelivery(id);
            return new CreditReceiptOutput(id, delivery.reward, delivery.state);
        }));
    }
}
