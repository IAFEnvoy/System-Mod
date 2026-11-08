package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.input.HelpInput;
import com.iafenvoy.system.data.store.AgentJournalDataStorage;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.chat.AiManagementCommands;
import com.iafenvoy.system.module.builtin.chat.FreeChatModule;
import com.iafenvoy.system.module.builtin.knowledge.DatabaseQueryTool;
import com.iafenvoy.system.module.builtin.knowledge.QuestionModule;
import com.iafenvoy.system.module.builtin.knowledge.ReasoningModule;
import com.iafenvoy.system.module.builtin.storage.DisplayCardsDataStorage;
import com.iafenvoy.system.module.builtin.storage.ModuleAccessDataStorage;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class CoreSystemModule implements CoreModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "core");
    public static final MapCodec<CoreSystemModule> CODEC = MapCodec.unit(new CoreSystemModule());
    public static final ModuleStorage<AgentJournalDataStorage> JOURNAL = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "agent_journal"), AgentJournalDataStorage.class, AgentJournalDataStorage.CODEC, AgentJournalDataStorage::new);
    public static final ModuleStorage<ModuleAccessDataStorage> ACCESS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "module_access"), ModuleAccessDataStorage.class, ModuleAccessDataStorage.CODEC, ModuleAccessDataStorage::new);
    public static final ModuleStorage<DisplayCardsDataStorage> DISPLAY_CARDS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "display_cards"), DisplayCardsDataStorage.class, DisplayCardsDataStorage.CODEC, DisplayCardsDataStorage::new);

    @Override
    public MapCodec<CoreSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(JOURNAL, ACCESS, DISPLAY_CARDS);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return context.get(ContextTypes.DISPLAYS);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new HelpModule(), new QuestionModule(new DatabaseQueryTool(context.get(ContextTypes.DATABASES)).tool()), new ReasoningModule(), new FreeChatModule(context.engine().chat()));
    }

    @Override
    public void installed(ModuleContext context) {
        context.engine().modules().root(HelpInput.INSTANCE);
        context.engine().modules().reject(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "semantic_reject"), RoutingProfile.hints(
                List.of("不要替我签到", "签到奖励有哪些", "如果签到会怎么样", "帮我建造房子"),
                List.of("Do not sign me in", "Explain daily sign-in rewards", "What happens if I sign in?", "Build me a house")));
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        ModuleManagementCommands.register(root);
        AiManagementCommands.register(root);
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
}
