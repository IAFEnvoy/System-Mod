package com.iafenvoy.system.assistant.tool.signin;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.SignInCreditInput;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import com.iafenvoy.system.module.builtin.storage.InboxDataStorage;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Commits the chosen catalog reward once per day after revalidating the player and catalog.
 */
public final class SignInCreditTool implements ToolRegistry.Handler<SignInCreditInput, RewardOutput> {
    private final CommerceCatalogs catalogs;

    public SignInCreditTool(CommerceCatalogs catalogs) {
        this.catalogs = catalogs;
    }

    public ToolRegistry.Tool<SignInCreditInput, RewardOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.SIGN_IN_CREDIT, true, true, false, this);
    }

    @Override
    public CompletionStage<RewardOutput> execute(ExecutionContext context, SignInCreditInput input) {
        String day = input.date();
        Identifier rewardId = input.rewardId();
        CommerceCatalog catalog = this.catalogs.available(context.player());
        if (!catalog.revision().equals(input.catalogRevision()))
            throw new AgentException(AgentFailure.CATALOG_CHANGED);
        PersistHolder<Reward> reward = catalog.rewards().stream().filter(value -> HolderHelper.id(value).equals(rewardId))
                .map(value -> value.value().reward(rewardId)).findFirst()
                .orElseThrow(() -> new AgentException(AgentFailure.MODEL_INVALID));
        context.ensureMemoryCurrent();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            context.check();
            if (!day.equals(LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString()))
                throw new AgentException(AgentFailure.EXPIRED);
            context.requireExternalServices();
            SignInDataStorage signIn = ModuleData.get(data, SignInSystemModule.STORAGE);
            if (signIn.hasReward(day)) return new RewardOutput(signIn.getReward(day));
            InboxDataStorage inbox = ModuleData.get(data, InboxSystemModule.STORAGE);
            inbox.assertDeliveryCount();
            signIn.putReward(day, reward);
            inbox.putDelivery("daily:" + day, new Delivery("daily:" + day, reward));
            return new RewardOutput(reward);
        }));
    }
}
