package com.iafenvoy.system.module.builtin.signin;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.signin.OfflineSignIn;
import com.iafenvoy.system.assistant.signin.SignIns;
import com.iafenvoy.system.data.input.SignInStatusInput;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.progress.SignInRules;
import com.iafenvoy.system.data.progression.ProgressionEstimate;
import com.iafenvoy.system.data.result.SignInStatus;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sign-in status is independent of the general system space and never performs a sign-in.
 */
public final class SignInStatusModule extends AbstractAgentModule<SignInStatusInput> {
    private final CommerceCatalogs catalogs;
    private final SignIns signIns;
    private final OfflineSignIn offline;
    private final SignInRules rules;

    public SignInStatusModule(CommerceCatalogs catalogs, SignIns signIns, OfflineSignIn offline, SignInRules rules) {
        super(SignInStatusInput.CODEC, "Read today's sign-in status and available reward packages without signing in.",
                List.of(), true, false, ExecutionPolicy.quick(0, 0));
        this.catalogs = catalogs;
        this.signIns = signIns;
        this.offline = offline;
        this.rules = rules;
    }

    @Override
    public ModuleRouting<SignInStatusInput> routing() {
        return ModuleRouting.<SignInStatusInput>defaults().button(_ -> SignInStatusInput.INSTANCE)
                .command(AgentCommand.literal(SignInStatusInput.INSTANCE, "signin_status", "签到状态"))
                .semantic(RoutingProfile.read(_ -> SignInStatusInput.INSTANCE,
                        List.of("我今天签到了吗", "查看签到状态", "看一下签到状态和补给"),
                        List.of("Have I signed in today?", "Check my daily sign-in status")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, SignInStatusInput input) {
        String day = BusinessCalendar.today().toString();
        Identifier fixed = context.store().read(context.player(), data -> this.rules.fixed(ModuleData.get(data, SignInSystemModule.STORAGE).progress()));
        if (fixed != null) {
            PersistHolder<Reward> earned = this.signIns.reward(context, day);
            if (earned != null)
                return this.result(context, day, List.of(earned), false, Set.of(), Optional.empty(), true);
            CommerceCatalogs.View view = this.catalogs.view(context);
            List<PersistHolder<Reward>> rewards = view.catalog().rewards().stream().filter(holder -> HolderHelper.id(holder).equals(fixed))
                    .map(holder -> holder.value().reward(fixed)).toList();
            return this.result(context, day, rewards, false, view.lockedRewards(), Optional.empty(), true);
        }
        if (!context.externalServicesAllowed()) {
            OfflineSignIn.Selection selection = this.offline.candidates(context);
            return this.result(context, day, selection.candidates(), false, Set.of(), Optional.of(selection.progression()), false);
        }
        CommerceCatalogs.View view = this.catalogs.view(context);
        return this.result(context, day, view.catalog().rewards().stream().map(holder -> holder.value().reward(HolderHelper.id(holder))).toList(),
                true, view.lockedRewards(), Optional.empty(), false);
    }

    private AgentResult result(ExecutionContext context, String day, List<PersistHolder<Reward>> candidates, boolean external,
                               Set<Identifier> locked, Optional<ProgressionEstimate> estimate, boolean fixed) {
        List<Component> activities = this.catalogs.view(context).catalog().rewards().stream()
                .flatMap(holder -> holder.value().conditions().activity().description().stream()
                        .map(value -> holder.value().label().copy().append(" · ").append(value))).limit(64).map(value -> (Component) value).toList();
        return context.store().read(context.player(), data -> {
            SignInProgress progress = ModuleData.get(data, SignInSystemModule.STORAGE).progress();
            ZoneId zone = BusinessCalendar.zone();
            return AgentResult.data("system.result.sign_in_status", new SignInStatus(day,
                    this.signIns.reward(context.store(), context.player(), data, day).isPresent(), candidates, data.saveHistory, external,
                    locked, estimate, progress, fixed, zone.getId(), this.rules.preview(progress, LocalDate.parse(day), zone), activities));
        });
    }
}
