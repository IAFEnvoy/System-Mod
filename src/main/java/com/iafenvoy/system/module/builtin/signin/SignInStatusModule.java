package com.iafenvoy.system.module.builtin.signin;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.input.SignInStatusInput;
import com.iafenvoy.system.data.result.SignInStatus;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.SignInSystemModule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Sign-in status is independent of the general system space and never performs a sign-in.
 */
public final class SignInStatusModule extends AbstractAgentModule<SignInStatusInput> {
    private final CommerceCatalogs catalogs;

    public SignInStatusModule(CommerceCatalogs catalogs) {
        super(SignInStatusInput.CODEC, "Read today's sign-in status and available reward packages without signing in.",
                List.of(), true, false, ExecutionPolicy.quick(0, 0));
        this.catalogs = catalogs;
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
        String day = LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString();
        CommerceCatalogs.View view = this.catalogs.view(context);
        return context.store().read(context.player(), data -> AgentResult.data("system.result.sign_in_status",
                new SignInStatus(day, ModuleData.get(data, SignInSystemModule.STORAGE).hasReward(day),
                        view.catalog().rewards().stream().map(holder -> holder.value().reward(HolderHelper.id(holder))).toList(),
                        data.saveHistory, context.permissions().effective(data.mode).usesAi(), view.lockedRewards())));
    }
}
