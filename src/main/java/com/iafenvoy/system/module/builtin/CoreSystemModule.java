package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.core.HelpModule;
import com.iafenvoy.system.agent.builtin.input.HelpInput;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.AgentJournalDataStorage;
import com.iafenvoy.system.module.builtin.storage.ModuleAccessDataStorage;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class CoreSystemModule implements CoreModule {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("system", "core");
    public static final MapCodec<CoreSystemModule> CODEC = MapCodec.unit(new CoreSystemModule());
    public static final ModuleStorage<AgentJournalDataStorage> JOURNAL = new ModuleStorage<>(ID, ResourceLocation.fromNamespaceAndPath("system", "agent_journal"), AgentJournalDataStorage.class, AgentJournalDataStorage.CODEC, AgentJournalDataStorage::new);
    public static final ModuleStorage<ModuleAccessDataStorage> ACCESS = new ModuleStorage<>(ID, ResourceLocation.fromNamespaceAndPath("system", "module_access"), ModuleAccessDataStorage.class, ModuleAccessDataStorage.CODEC, ModuleAccessDataStorage::new);

    @Override
    public MapCodec<CoreSystemModule> codec() {
        return CODEC;
    }

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
        context.engine().modules().reject(ResourceLocation.fromNamespaceAndPath("system", "semantic_reject"), RoutingProfile.hints(
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
                .then(Commands.literal("routing").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
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
