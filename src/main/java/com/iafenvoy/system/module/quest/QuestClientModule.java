package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.command.client.AssistantCommandSuggestions;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public final class QuestClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        // These nodes contribute current quest candidates without intercepting server execution.
        for (String alias : List.of("view", "查看", "submit", "提交", "提交任务"))
            module.suggest(LiteralArgumentBuilder.<AssistantCommandSource>literal(alias)
                    .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("quest", StringArgumentType.greedyString())
                            .suggests(QuestClientModule::quests)));
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
