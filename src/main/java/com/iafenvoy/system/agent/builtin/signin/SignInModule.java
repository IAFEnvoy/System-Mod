package com.iafenvoy.system.agent.builtin.signin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.input.SignInCreditInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.builtin.output.RewardOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.module.signin.SignInSystemModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Selects an existing daily reward and credits it through the validated write tool.
 */
public final class SignInModule extends BuiltinModule<EmptyInput> {
    public SignInModule(ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player, ToolRegistry.Tool<SignInCreditInput, RewardOutput> credit) {
        super(ResultTypes.SIGN_IN_ACTION, "Perform today's AI sign-in ONLY when explicitly requested. For status/reward questions use system:inbox.",
                EmptyInput.CODEC, List.of(player, credit), true, false, ExecutionPolicy.quick(2, 1));
    }

    @Override
    public ModuleRouting<EmptyInput> routing() {
        return ModuleRouting.<EmptyInput>defaults()
                .command(AgentCommand.literal(EmptyInput.INSTANCE, "signin", "签到"))
                .semantic(RoutingProfile.hints(
                        List.of("帮我签个到", "领取今天的签到补给", "现在执行每日签到", "给我结算今日签到"),
                        List.of("Sign me in for today", "Perform my daily sign-in", "Give me today's daily supplies", "Settle my daily sign-in")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, EmptyInput input) throws Exception {
        // AI selects an existing reward ID; the server owns quantities and commits the daily entitlement.
        ZoneId zone = ZoneId.of(AgentConfig.INSTANCE.get().timeZone());
        String day = LocalDate.now(zone).toString();
        PlayerData.Reward existing = context.store().read(context.player(), data -> ModuleData.get(data, SignInSystemModule.STORAGE).rewards.get(day));
        if (existing != null)
            return AgentResult.data("system.result.already", ResultTypes.REWARD.of(new RewardOutput(existing)));
        CommerceCatalog catalog = context.catalog();
        if (catalog.rewards().isEmpty()) throw new AgentException("CAPABILITY_UNAVAILABLE");
        SignInDecision.Player state = SignInDecision.Player.of(context.tool(BuiltinTools.PLAYER, "snapshot", input));
        SignInDecision decision = context.model(context.prompt(SystemPrompts.SIGN_IN), SignInDecision.Input.CODEC,
                new SignInDecision.Input(state, catalog.rewards(), day), SignInDecision.CODEC);
        Identifier selectedId = decision.rewardId();
        String reason = com.iafenvoy.saturn.util.MiscUtil.clip(decision.reason(), 240);
        PlayerData.Reward reward = catalog.rewards().stream().filter(value -> value.id().equals(selectedId)).findFirst()
                .orElseThrow(() -> new AgentException("MODEL_INVALID"));
        // Recheck date, player and catalog after inference; a successful reply alone does not grant a reward.
        context.ensureMemoryCurrent();
        if (!day.equals(LocalDate.now(zone).toString())) throw new AgentException("EXPIRED");
        context.tool(BuiltinTools.PLAYER, "revalidate", input);
        RewardOutput committed = context.tool(BuiltinTools.SIGN_IN_CREDIT, "credit", new SignInCreditInput(day, reward.id(), catalog.revision()));
        PlayerData.Reward chosen = committed.reward();
        return new AgentResult("SUCCEEDED", "system.result.sign_in", reason,
                List.of(new AgentResult.Card("system.result.reward", ResultTypes.REWARD.of(new RewardOutput(chosen)), List.of(
                        new AgentResult.Action("system.action.deliver", ResultTypes.DELIVER_ACTION, new EntitlementInput("daily:" + day))))));
    }
}
