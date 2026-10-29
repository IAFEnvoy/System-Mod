package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.command.client.AssistantCommandSuggestions;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.localAction(Identifier.parse("system:close"), (source, _) -> AssistantMessages.dismissCard(source.id()));
        module.localAction(Identifier.parse("system:open"), (_, _) -> AssistantClient.open(AssistantClient.simpleMode(), ""));
        module.localAction(Identifier.parse("system:workspace"), (_, arguments) -> {
            AssistantClient.open(false, "");
            if (Minecraft.getInstance().screen instanceof AssistantScreen screen)
                screen.showPage(arguments.getOrDefault("page", "chat"));
        });
        module.command(() -> AssistantClient.open(true, ""), "mode simple", "simple", "简约模式");
        module.command(() -> AssistantClient.open(false, ""), "mode full", "full", "完整模式");
        module.command(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) screen.onClose();
        }, "close", "关闭界面");
        for (String name : List.of("select", "选择"))
            module.command(LiteralArgumentBuilder.<AssistantCommandSource>literal(name).then(selection()));
        module.command(selection(reader -> {
            // Retain compact Chinese choices such as 选择1 through an ordinary argument parser.
            if (reader.canRead(2) && reader.getString().startsWith("选择", reader.getCursor()))
                reader.setCursor(reader.getCursor() + 2);
            return IntegerArgumentType.integer().parse(reader);
        }));
        module.workspace("chat", "nav_chat", WorkspaceGroup.CHAT, workspace -> {
            workspace.labels("nav_chat");
            workspace.click("nav_chat", () -> workspace.show("chat"));
        });
    }

    private static RequiredArgumentBuilder<AssistantCommandSource, Integer> selection() {
        return selection(IntegerArgumentType.integer());
    }

    private static RequiredArgumentBuilder<AssistantCommandSource, Integer> selection(ArgumentType<Integer> argument) {
        return RequiredArgumentBuilder.<AssistantCommandSource, Integer>argument("selection", argument)
                .suggests(CoreSystemClientModule::choices)
                .executes(context -> select(IntegerArgumentType.getInteger(context, "selection") - 1));
    }

    private static CompletableFuture<Suggestions> choices(CommandContext<AssistantCommandSource> context, SuggestionsBuilder builder) {
        List<ClientCards.Choice> choices = AssistantClient.state().currentActions();
        for (int index = 0; index < choices.size(); index++)
            AssistantCommandSuggestions.suggest(builder, Integer.toString(index + 1), AssistantView.tr(choices.get(index).labelKey()));
        return builder.buildFuture();
    }

    private static int select(int index) {
        List<ClientCards.Choice> actions = AssistantClient.state().currentActions();
        if (index < 0 || index >= actions.size()) AssistantClient.local(AgentResult.error("INVALID_SELECTION"));
        else actions.get(index).execute();
        return 1;
    }
}
