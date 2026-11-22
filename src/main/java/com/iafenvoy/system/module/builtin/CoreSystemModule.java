package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.command.AiManagementCommands;
import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.routing.RoutingDiagnostics;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.assistant.util.CodecIO;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.progress.PlayerFacts;
import com.iafenvoy.system.command.ActivityCommands;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.HelpInput;
import com.iafenvoy.system.data.store.AgentJournalDataStorage;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.chat.FreeChatModule;
import com.iafenvoy.system.module.builtin.knowledge.DatabaseQueryTool;
import com.iafenvoy.system.module.builtin.knowledge.QuestionModule;
import com.iafenvoy.system.module.builtin.knowledge.ReasoningModule;
import com.iafenvoy.system.module.builtin.progress.RecordClaimModule;
import com.iafenvoy.system.module.builtin.progress.RecordQueryModule;
import com.iafenvoy.system.module.builtin.storage.DisplayCardsDataStorage;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import com.iafenvoy.system.module.builtin.storage.ModuleAccessDataStorage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreSystemModule implements CoreModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "core");
    public static final MapCodec<CoreSystemModule> CODEC = MapCodec.unit(new CoreSystemModule());
    public static final ModuleStorage<AgentJournalDataStorage> JOURNAL = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "agent_journal"), AgentJournalDataStorage.class, AgentJournalDataStorage.CODEC, AgentJournalDataStorage::new);
    public static final ModuleStorage<ModuleAccessDataStorage> ACCESS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "module_access"), ModuleAccessDataStorage.class, ModuleAccessDataStorage.CODEC, ModuleAccessDataStorage::new);
    public static final ModuleStorage<DisplayCardsDataStorage> DISPLAY_CARDS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "display_cards"), DisplayCardsDataStorage.class, DisplayCardsDataStorage.CODEC, DisplayCardsDataStorage::new);
    public static final ModuleStorage<FactDataStorage> FACTS = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "facts"), FactDataStorage.class, FactDataStorage.CODEC, FactDataStorage::new);

    @Override
    public MapCodec<CoreSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(JOURNAL, ACCESS, DISPLAY_CARDS, FACTS);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        PlayerFacts.service(context);
        return context.get(ContextTypes.DISPLAYS);
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        return List.of(new HelpModule(), new QuestionModule(new DatabaseQueryTool(context.get(ContextTypes.DATABASES)).tool()), new ReasoningModule(), new FreeChatModule(context.engine().chat()),
                new RecordQueryModule(new ToolRegistry.Tool<>(BuiltinTools.RECORD_QUERY, false, true, false,
                        (execution, input) -> CompletableFuture.completedFuture(PlayerFacts.query(execution, input.offset())))),
                new RecordClaimModule(new ToolRegistry.Tool<>(BuiltinTools.RECORD_CLAIM, true, true, false,
                        (execution, input) -> CompletableFuture.completedFuture(PlayerFacts.claim(execution, input)))));
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
        AiManagementCommands.register(root, ModuleCommands::runtime);
        ActivityCommands.register(root);
        root.executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.CURRENT))
                .then(Commands.literal("simple").executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.SIMPLE)))
                .then(Commands.literal("full").executes(context -> open(context.getSource().getPlayerOrException(), OpenResponse.View.FULL)))
                .then(Commands.literal("routing").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                    if (runtime == null) {
                        context.getSource().sendFailure(Component.translatable("system.error.capability_unavailable"));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal(CodecIO.encode(RoutingDiagnostics.CODEC, runtime.engine().routingDiagnostics())), false);
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
