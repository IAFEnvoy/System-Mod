package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.builtin.core.HelpModule;
import com.iafenvoy.system.agent.builtin.input.HelpInput;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.command.client.AssistantCommandSuggestions;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.AgentJournalDataStorage;
import com.iafenvoy.system.module.builtin.storage.ModuleAccessDataStorage;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreSystemModule implements CoreModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "core");
    public static final ModuleStorage<AgentJournalDataStorage> JOURNAL = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath("system", "agent_journal"), AgentJournalDataStorage.class, AgentJournalDataStorage.CODEC, AgentJournalDataStorage::new);
    public static final ModuleStorage<ModuleAccessDataStorage> ACCESS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath("system", "module_access"), ModuleAccessDataStorage.class, ModuleAccessDataStorage.CODEC, ModuleAccessDataStorage::new);

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(JOURNAL, ACCESS);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new HelpModule());
    }

    @Override
    public void installed(ModuleContext context) {
        context.engine().modules().root(HelpInput.INSTANCE);
        context.engine().modules().reject(Identifier.fromNamespaceAndPath("system", "semantic_reject"), RoutingProfile.hints(
                List.of("不要替我签到", "签到奖励有哪些", "如果签到会怎么样", "查询配方如何合成", "帮我建造房子", "我想问一个游戏攻略"),
                List.of("Do not sign me in", "Explain daily sign-in rewards", "What happens if I sign in?", "How do I craft this recipe?", "Build me a house", "Tell me a game strategy")));
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        ModuleManagementCommands.register(root);
        root.executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.CURRENT))
                .then(Commands.literal("simple").executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.SIMPLE)))
                .then(Commands.literal("full").executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.FULL)))
                .then(Commands.literal("ask").then(Commands.argument("message", StringArgumentType.greedyString()).executes(context ->
                        ModuleCommands.ask(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")))))
                .then(Commands.literal("routing").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                    if (runtime == null) {
                        context.getSource().sendFailure(Component.translatable("system.error.capability_unavailable"));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal(CodecIO.write(RoutingDiagnostics.CODEC, runtime.engine().routingDiagnostics())), false);
                    return 1;
                }));
    }

    private static int open(ServerPlayer player, OpenResponse.View view) {
        AssistantServer runtime = ModuleCommands.runtime(player);
        if (runtime == null) return 0;
        runtime.requests().open(player, view);
        return 1;
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
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
                    .suggests(ClientHooks::choices)
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
}
