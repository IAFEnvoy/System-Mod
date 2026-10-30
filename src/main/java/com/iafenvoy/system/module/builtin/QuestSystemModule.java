package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.SharedModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * This definition is safe without FTB; only the checked factory resolves optional dependency classes.
 */
public final class QuestSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "ftb_quests");
    public static final MapCodec<QuestSystemModule> CODEC = MapCodec.unit(new QuestSystemModule());

    @Override
    public MapCodec<QuestSystemModule> codec() {
        return CODEC;
    }

    @Override
    public boolean available() {
        return ModList.get().isLoaded("ftbquests");
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("ftb").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(context -> reloadKnowledge(context.getSource()))));
    }

    private static int reloadKnowledge(CommandSourceStack source) {
        AssistantServer runtime = AssistantServer.get(source.getServer());
        if (runtime == null) {
            source.sendFailure(Component.translatable("system.error.capability_unavailable"));
            return 0;
        }
        try {
            Object result = Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter")
                    .getMethod("reloadKnowledge", MinecraftServer.class).invoke(null, source.getServer());
            @SuppressWarnings("unchecked") CompletionStage<Integer> reloading = (CompletionStage<Integer>) result;
            source.sendSuccess(() -> Component.translatable("system.command.ftb_reloading"), false);
            reloading.whenComplete((count, error) -> source.getServer().execute(() -> {
                if (error == null)
                    source.sendSuccess(() -> Component.translatable("system.command.ftb_reloaded", count), true);
                else {
                    SystemMod.LOGGER.warn("Unable to reload FTB knowledge", error);
                    source.sendFailure(Component.translatable("system.command.ftb_reload_failed"));
                }
            }));
            return 1;
        } catch (ReflectiveOperationException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            if (AgentEngine.errorCode(cause).equals("BUSY"))
                source.sendFailure(Component.translatable("system.error.busy"));
            else {
                SystemMod.LOGGER.warn("Unable to start FTB knowledge reload", cause);
                source.sendFailure(Component.translatable("system.command.ftb_reload_failed"));
            }
            return 0;
        }
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        try {
            return (PlayerModule) Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter").getMethod("runtime", MinecraftServer.class).invoke(null, context.game().server());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("FTB Quests module runtime is unavailable", error);
        }
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        try {
            Object result = Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter")
                    .getMethod("modules", ModuleContext.class).invoke(null, context);
            @SuppressWarnings("unchecked") List<AgentModule<?>> modules = (List<AgentModule<?>>) result;
            return List.copyOf(modules);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("FTB Quests assistant adapter is unavailable", error);
        }
    }
}
