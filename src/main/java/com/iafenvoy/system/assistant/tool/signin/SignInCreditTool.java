package com.iafenvoy.system.assistant.tool.signin;

import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.progress.PlayerFacts;
import com.iafenvoy.system.assistant.signin.OfflineSignIn;
import com.iafenvoy.system.assistant.signin.SignIns;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.SignInCreditInput;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.progress.SignInRules;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Commits the chosen catalog reward once per day after revalidating the player and catalog.
 */
public final class SignInCreditTool implements ToolRegistry.Handler<SignInCreditInput, RewardOutput, ExecutionContext> {
    private final CommerceCatalogs catalogs;
    private final SignIns signIns;
    private final OfflineSignIn offline;
    private final SignInRules rules;
    private final Identifier definition;

    public SignInCreditTool(CommerceCatalogs catalogs, SignIns signIns, OfflineSignIn offline, SignInRules rules, Identifier definition) {
        this.catalogs = catalogs;
        this.signIns = signIns;
        this.offline = offline;
        this.rules = rules;
        this.definition = definition;
    }

    public ToolRegistry.Tool<SignInCreditInput, RewardOutput, ExecutionContext> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.SIGN_IN_CREDIT, true, true, false, this);
    }

    @Override
    public CompletionStage<RewardOutput> execute(ExecutionContext context, SignInCreditInput input) {
        String day = input.date();
        Identifier rewardId = input.rewardId();
        context.ensureMemoryCurrent();
        PersistHolder<Reward> existing = this.signIns.reward(context, day);
        if (existing != null) return CompletableFuture.completedFuture(new RewardOutput(existing));
        CommerceCatalog catalog = this.catalogs.available(context.player());
        if (!catalog.revision().equals(input.catalogRevision()))
            throw new AgentException(AgentFailure.CATALOG_CHANGED);
        PersistHolder<Reward> reward = this.catalogs.requireReward(context, rewardId, false).value().reward(rewardId);
        Identifier fixed = context.store().read(context.player(), data -> this.rules.fixed(ModuleData.get(data, SignInSystemModule.STORAGE).progress()));
        if (fixed != null) {
            if (!fixed.equals(rewardId)) throw new AgentException(AgentFailure.CATALOG_CHANGED);
        } else if (input.offline()) {
            OfflineSignIn.Selection selection = this.offline.candidates(context);
            if (!selection.revision().equals(input.catalogRevision()) || selection.candidates().stream().noneMatch(value -> value.id().equals(rewardId)))
                throw new AgentException(AgentFailure.SIGN_IN_NO_REWARD);
        } else context.requireExternalServices();
        long now = System.currentTimeMillis();
        PlayerFact fact = new PlayerFact(PlayerFacts.SIGNED_IN, SignInSystemModule.ID, day, day, now);
        List<PlayerFacts.Award> awards = this.catalogs.access().read(context, player -> PlayerFacts.prepare(player, fact));
        context.ensureMemoryCurrent();
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            context.check();
            if (!day.equals(BusinessCalendar.today().toString()))
                throw new AgentException(AgentFailure.EXPIRED);
            if (fixed == null && !input.offline()) context.requireExternalServices();
            SignInDataStorage signIn = ModuleData.get(data, SignInSystemModule.STORAGE);
            PersistHolder<Reward> prior = this.signIns.reward(context.store(), context.player(), data, day).orElse(null);
            if (prior != null) return new RewardOutput(prior);
            if (!Objects.equals(fixed, this.rules.fixed(signIn.progress())))
                throw new AgentException(AgentFailure.CATALOG_CHANGED);
            SignInProgress progress = signIn.progress().next(LocalDate.parse(day), BusinessCalendar.zone());
            this.signIns.prepareCredit(context.store(), context.player(), data);
            SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
            inbox.assertDeliveryCount();
            signIn.putReward(day, reward);
            signIn.progress(progress);
            SignIns.milestones(data, this.definition, this.rules, progress, now);
            PlayerFacts.record(data, fact, awards);
            inbox.putDelivery("daily:" + day, new Delivery("daily:" + day, reward));
            return new RewardOutput(reward);
        }));
    }
}
