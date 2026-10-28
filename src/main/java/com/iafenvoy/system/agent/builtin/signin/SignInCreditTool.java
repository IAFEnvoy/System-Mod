package com.iafenvoy.system.agent.builtin.signin;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.SignInCreditInput;
import com.iafenvoy.system.agent.builtin.output.RewardOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.module.inbox.InboxSystemModule;
import com.iafenvoy.system.module.signin.SignInSystemModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Commits the chosen catalog reward once per day after revalidating the player and catalog.
 */
public final class SignInCreditTool implements ToolRegistry.Handler<SignInCreditInput, RewardOutput> {
    public ToolRegistry.Tool<SignInCreditInput, RewardOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.SIGN_IN_CREDIT, true, true, false, this);
    }

    @Override
    public CompletionStage<RewardOutput> execute(ExecutionContext context, SignInCreditInput input) {
        String day = input.date();
        Identifier rewardId = input.rewardId();
        CommerceCatalog catalog = context.catalog();
        if (!catalog.revision().equals(input.catalogRevision()))
            throw new AgentException("CATALOG_CHANGED");
        PlayerData.Reward reward = catalog.rewards().stream().filter(value -> value.id().equals(rewardId)).findFirst()
                .orElseThrow(() -> new AgentException("MODEL_INVALID"));
        context.ensureMemoryCurrent();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            context.check();
            if (!day.equals(LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString()))
                throw new AgentException("EXPIRED");
            if (!data.externalModel) throw new AgentException("MODEL_DISABLED");
            if (ModuleData.get(data, SignInSystemModule.STORAGE).rewards.containsKey(day)) return new RewardOutput(ModuleData.get(data, SignInSystemModule.STORAGE).rewards.get(day));
            if (ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.size() >= 4096) throw new AgentException("STORAGE_FULL");
            ModuleData.get(data, SignInSystemModule.STORAGE).rewards.put(day, reward);
            ModuleData.get(data, InboxSystemModule.STORAGE).deliveries.put("daily:" + day, new PlayerData.Delivery("daily:" + day, reward));
            return new RewardOutput(reward);
        }));
    }
}
