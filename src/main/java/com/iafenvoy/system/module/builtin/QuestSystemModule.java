package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.command.client.AssistantCommandSuggestions;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This definition is safe without FTB; only the checked factory resolves optional dependency classes.
 */
public final class QuestSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "ftb_quests");

    @Override
    public boolean available() {
        return ModList.get().isLoaded("ftbquests");
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
                    .getMethod("modules", AgentEngine.class, MinecraftTools.class).invoke(null, context.engine(), context.game());
            @SuppressWarnings("unchecked") List<AgentModule<?>> modules = (List<AgentModule<?>>) result;
            return List.copyOf(modules);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("FTB Quests assistant adapter is unavailable", error);
        }
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            // These nodes contribute current quest candidates without intercepting server execution.
            for (String alias : List.of("view", "查看", "submit", "提交", "提交任务"))
                module.suggest(LiteralArgumentBuilder.<AssistantCommandSource>literal(alias)
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("quest", StringArgumentType.greedyString())
                                .suggests(ClientHooks::quests)));
            module.workspace("quests", "nav_quests", workspace -> {
                Runnable refresh = () -> AssistantClient.send(new QuestQueryInput("", false), "");
                workspace.labels("nav_quests", "quest_refresh", "quest_claimable");
                workspace.click("nav_quests", () -> {
                    workspace.show("quests");
                    refresh.run();
                });
                workspace.click("quest_refresh", refresh);
                workspace.click("quest_claimable", () -> AssistantClient.send(new QuestQueryInput("", true), ""));
            });
        }

        private static CompletableFuture<Suggestions> quests(CommandContext<AssistantCommandSource> context, SuggestionsBuilder builder) {
            AssistantClientState.ChatEntry current = AssistantClient.state().current();
            if (current == null || current.result() == null) return builder.buildFuture();
            for (CardUpdate card : current.result().cards()) {
                if (!(ClientCards.latest(card).data().orElse(null) instanceof ResultTypes.QuestList(
                        List<QuestBriefOutput> quests
                ))) continue;
                for (QuestBriefOutput quest : quests) {
                    AssistantCommandSuggestions.suggest(builder, Integer.toString(quest.number()), quest.title().getString());
                    AssistantCommandSuggestions.suggest(builder, quest.title().getString(), "");
                }
            }
            return builder.buildFuture();
        }
    }
}
