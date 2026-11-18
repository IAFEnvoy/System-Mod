package com.iafenvoy.system.command;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.output.PaymentQuote;
import com.iafenvoy.system.data.store.AnchorTravel;
import com.iafenvoy.system.data.store.OnlineJobRun;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.AnchorSystemModule;
import com.iafenvoy.system.module.builtin.OnlineJobsSystemModule;
import com.iafenvoy.system.module.builtin.storage.AnchorDataStorage;
import com.iafenvoy.system.module.builtin.storage.OnlineJobsDataStorage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Manual review never repeats a payment, refunds uncertain fees, or teleports the player.
 */
public final class ActivityCommands {
    private ActivityCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> activity = Commands.literal("activity").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        activity.then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player()).executes(ActivityCommands::show)));
        for (String decision : new String[]{"job_paid", "job_unpaid", "close_travel"})
            activity.then(Commands.literal(decision).then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(context -> resolve(context, decision))))));
        root.then(activity);
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        runtime.engine().maintenance(() -> runtime.engine().playerStore(target.getUUID()).read(target.getUUID(), data -> {
            List<OnlineJobRun> jobs = ModuleData.get(data, OnlineJobsSystemModule.STORAGE).getRuns().stream().filter(run -> !run.claimed()).toList();
            Optional<AnchorTravel> travel = ModuleData.get(data, AnchorSystemModule.STORAGE).getTravel();
            MutableComponent text = Component.empty();
            jobs.forEach(value -> text.append(Component.literal("job " + value.id() + " [" + value.state() + "] ")).append(value.job().value().label())
                    .append(" · ").append(PaymentQuote.describe(value.price())).append("\n"));
            travel.ifPresent(value -> text.append(Component.literal("travel " + value.id() + " [" + value.state() + "] " + value.destination().name() + " · "))
                    .append(PaymentQuote.describe(value.price())).append("\n"));
            if (jobs.isEmpty() && travel.isEmpty()) text.append(Component.translatable("system.activity.empty"));
            return text;
        })).whenComplete((text, error) -> source.getServer().execute(() -> {
            if (error != null) source.sendFailure(AgentException.failure(error).message());
            else source.sendSuccess(() -> text, false);
        }));
        return 1;
    }

    private static int resolve(CommandContext<CommandSourceStack> context, String decision) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        String id = StringArgumentType.getString(context, "id");
        String reason = StringArgumentType.getString(context, "reason").strip();
        if (reason.isEmpty() || reason.length() > 512) throw new AgentException(AgentFailure.INVALID_INPUT);
        String reviewer = source.getTextName();
        runtime.engine().maintenance(() -> runtime.engine().playerStore(target.getUUID()).update(target.getUUID(), data -> {
            if (decision.equals("close_travel")) {
                AnchorDataStorage storage = ModuleData.get(data, AnchorSystemModule.STORAGE);
                AnchorTravel travel = storage.getTravel().filter(value -> value.id().equals(id) && value.state() == AnchorTravel.State.UNKNOWN)
                        .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
                storage.setTravel(Optional.of(travel.state(AnchorTravel.State.COMPLETED)));
            } else {
                OnlineJobsDataStorage storage = ModuleData.get(data, OnlineJobsSystemModule.STORAGE);
                OnlineJobRun job = storage.find(id);
                if (job.state() != OnlineJobRun.State.UNKNOWN) throw new AgentException(AgentFailure.STALE_REFERENCE);
                if (decision.equals("job_paid")) storage.replace(job.state(OnlineJobRun.State.QUEUED));
                else storage.remove(id);
            }
            return true;
        })).whenComplete((_, error) -> source.getServer().execute(() -> {
            if (error != null) source.sendFailure(AgentException.failure(error).message());
            else {
                SystemMod.LOGGER.warn("Activity manually reviewed: player={}, id={}, decision={}, reviewer={}, reason={}", target.getUUID(), id, decision, reviewer, reason);
                source.sendSuccess(() -> Component.translatable("system.activity.resolved", id), true);
            }
        }));
        return 1;
    }
}
