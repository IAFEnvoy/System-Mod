package com.iafenvoy.system.module.builtin.signin;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.signin.OfflineSignIn;
import com.iafenvoy.system.assistant.signin.SignIns;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.input.SignInCreditInput;
import com.iafenvoy.system.data.input.SignInInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.data.output.SignInDecision;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.progress.SignInRules;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.result.SignInStatus;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Selects an existing daily reward and credits it through the validated write tool.
 */
public final class SignInModule extends AbstractAgentModule<SignInInput> {
    private static final Set<String> EXPLICIT_REQUESTS = Set.of("签到", "每日签到", "帮我签到", "帮我签个到", "领取今天的签到补给", "现在执行每日签到", "给我结算今日签到",
            "sign in", "daily sign-in", "sign me in", "sign me in for today", "perform my daily sign-in", "give me today's daily supplies", "settle my daily sign-in");
    private final CommerceCatalogs catalogs;
    private final SignIns signIns;
    private final OfflineSignIn offline;
    private final SignInRules rules;

    public SignInModule(CommerceCatalogs catalogs, SignIns signIns, OfflineSignIn offline, SignInRules rules, ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player, ToolRegistry.Tool<SignInCreditInput, RewardOutput> credit) {
        super(SignInInput.CODEC, "Perform today's sign-in ONLY when explicitly requested. Offline mode uses local progression-based rewards; AI modes use the model. For sign-in status use system:sign_in_status; for stored contents use system:inbox.", List.of(player, credit), true, false, ExecutionPolicy.quick(2, 1));
        this.catalogs = catalogs;
        this.signIns = signIns;
        this.offline = offline;
        this.rules = rules;
    }

    @Override
    public ModuleRouting<SignInInput> routing() {
        return ModuleRouting.<SignInInput>defaults()
                .button(_ -> SignInInput.INSTANCE)
                .command(AgentCommand.literal(SignInInput.INSTANCE, "signin", "签到"))
                .explicit(text -> EXPLICIT_REQUESTS
                        .contains(text.strip().toLowerCase(Locale.ROOT).replaceAll("[。！!?.？]+$", "")) ? SignInInput.INSTANCE : null)
                .semantic(RoutingProfile.hints(
                        List.of("帮我签个到", "领取今天的签到补给", "现在执行每日签到", "给我结算今日签到"),
                        List.of("Sign me in for today", "Perform my daily sign-in", "Give me today's daily supplies", "Settle my daily sign-in")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, SignInInput input) throws Exception {
        // Both selectors use existing packages and share the same server-owned daily entitlement.
        ZoneId zone = BusinessCalendar.zone();
        String day = LocalDate.now(zone).toString();
        PersistHolder<Reward> existing = this.signIns.reward(context, day);
        if (existing != null)
            return this.result(context, "system.result.already", day, new RewardOutput(existing, Optional.of("daily:" + day), Component.translatable("system.result.already"), ""), false);
        Identifier fixed = context.store().read(context.player(), data -> this.rules.fixed(ModuleData.get(data, SignInSystemModule.STORAGE).progress()));
        if (fixed != null) {
            RewardOutput committed = context.tool(BuiltinTools.SIGN_IN_CREDIT, "credit",
                    new SignInCreditInput(day, fixed, this.catalogs.available(context.player()).revision(), true));
            return this.result(context, "system.result.sign_in", day, new RewardOutput(committed.reward(), Optional.of("daily:" + day), Component.translatable("system.result.sign_in"), ""), false);
        }
        if (!context.externalServicesAllowed()) {
            OfflineSignIn.Selection selection = this.offline.candidates(context);
            if (selection.candidates().isEmpty()) throw new AgentException(AgentFailure.SIGN_IN_NO_REWARD);
            Random random = new Random(context.player().getMostSignificantBits() ^ context.player().getLeastSignificantBits() ^ LocalDate.parse(day).toEpochDay());
            PersistHolder<Reward> reward = selection.candidates().get(random.nextInt(selection.candidates().size()));
            RewardOutput committed = context.tool(BuiltinTools.SIGN_IN_CREDIT, "credit",
                    new SignInCreditInput(day, reward.id(), selection.revision(), true));
            return this.result(context, "system.result.sign_in", day, new RewardOutput(committed.reward(), Optional.of("daily:" + day),
                    Component.translatable("system.result.sign_in"), ""), false);
        }
        CommerceCatalogs.View view = this.catalogs.view(context);
        CommerceCatalog catalog = view.catalog();
        if (catalog.rewards().isEmpty()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        ArrayList<Holder<SignInRewardEntry>> eligible = new ArrayList<>(catalog.rewards().stream()
                .filter(holder -> !view.lockedRewards().contains(HolderHelper.id(holder)))
                .toList());
        if (eligible.isEmpty()) throw new AgentException(AgentFailure.CONDITION_NOT_MET);
        // Stable per-player/day sampling gives every eligible entry a chance without favoring registry order.
        Collections.shuffle(eligible, new Random(context.player().getMostSignificantBits() ^ context.player().getLeastSignificantBits() ^ LocalDate.parse(day).toEpochDay()));
        List<PersistHolder<Reward>> rewards = eligible.stream().limit(32).map(holder -> holder.value().reward(HolderHelper.id(holder))).toList();
        List<SignInDecision.Candidate> summaries = this.catalogs.access().read(context, _ -> {
            List<SignInDecision.Candidate> result = new ArrayList<>();
            for (int index = 0; index < rewards.size(); index++)
                result.add(SignInDecision.Candidate.of(index, rewards.get(index)));
            return result;
        });
        SignInDecision.Player state = SignInDecision.Player.of(context.tool(BuiltinTools.PLAYER, "snapshot", EmptyInput.INSTANCE));
        String prompt = context.prompt(SystemPrompts.SIGN_IN);
        List<SignInDecision.Candidate> candidates = new ArrayList<>();
        Map<Identifier, PersistHolder<Reward>> trusted = new HashMap<>();
        for (int index = 0; index < summaries.size(); index++) {
            SignInDecision.Candidate candidate = summaries.get(index);
            candidates.add(candidate);
            if (context.fitsModelInput(prompt, SignInDecision.Input.CODEC, new SignInDecision.Input(state, candidates, day)))
                trusted.put(candidate.rewardId(), rewards.get(index));
            else candidates.removeLast();
        }
        if (candidates.isEmpty()) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
        SignInDecision decision = context.model(prompt, SignInDecision.Input.CODEC, new SignInDecision.Input(state, candidates, day), SignInDecision.CODEC);
        Identifier selectedId = decision.rewardId();
        String reason = MiscUtil.clip(decision.reason(), 240);
        PersistHolder<Reward> reward = Optional.ofNullable(trusted.get(selectedId)).orElseThrow(() -> new AgentException(AgentFailure.MODEL_INVALID));
        // Recheck date, player and catalog after inference; a successful reply alone does not grant a reward.
        context.ensureMemoryCurrent();
        if (!day.equals(LocalDate.now(zone).toString())) throw new AgentException(AgentFailure.EXPIRED);
        context.tool(BuiltinTools.PLAYER, "revalidate", EmptyInput.INSTANCE);
        RewardOutput committed = context.tool(BuiltinTools.SIGN_IN_CREDIT, "credit", new SignInCreditInput(day, reward.id(), catalog.revision()));
        PersistHolder<Reward> chosen = committed.reward();
        return this.result(context, "system.result.sign_in", day, new RewardOutput(chosen, Optional.of("daily:" + day),
                Component.translatable("system.result.sign_in"), reason), true);
    }

    private AgentResult result(ExecutionContext context, String key, String day, RewardOutput reward, boolean external) {
        return context.store().read(context.player(), data -> {
            SignInProgress progress = ModuleData.get(data, SignInSystemModule.STORAGE).progress();
            ZoneId zone = BusinessCalendar.zone();
            return AgentResult.of(key, reward, new SignInStatus(day, true, List.of(), data.saveHistory, external, Set.of(), Optional.empty(),
                    progress, !this.rules.dailyCycle().isEmpty(), zone.getId(), this.rules.preview(progress, LocalDate.parse(day), zone)));
        });
    }
}
