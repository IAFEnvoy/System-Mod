package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.space.SystemSpace;
import com.iafenvoy.system.assistant.space.SpaceDeposits;
import com.iafenvoy.system.command.SpaceCommands;
import com.iafenvoy.system.assistant.tool.space.DeliverTool;
import com.iafenvoy.system.data.input.SpaceInput;
import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.input.SpaceInventoryInput;
import com.iafenvoy.system.data.input.SpaceDepositInput;
import com.iafenvoy.system.data.input.RewardChoiceInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.data.output.RewardChoiceOutput;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.space.DeliverModule;
import com.iafenvoy.system.module.builtin.space.SpaceModule;
import com.iafenvoy.system.module.builtin.space.SpaceTakeModule;
import com.iafenvoy.system.module.builtin.space.SpaceInventoryModule;
import com.iafenvoy.system.module.builtin.space.SpaceDepositModule;
import com.iafenvoy.system.module.builtin.space.RewardChoiceModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record SpaceSystemModule(int depositSlots) implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox");
    public static final MapCodec<SpaceSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 1024).optionalFieldOf("deposit_slots", 256).forGetter(SpaceSystemModule::depositSlots)
    ).apply(i, SpaceSystemModule::new));
    public static final ModuleStorage<SpaceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), SpaceDataStorage.class, SpaceDataStorage.CODEC, SpaceDataStorage::new);

    @Override
    public MapCodec<SpaceSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        SystemSpace space = context.sharedRuntime(SystemSpace.class, () -> new SystemSpace(context.engine(), context.get(ContextTypes.GAME), context.get(ContextTypes.COMMERCE), SignInSystemModule.service(context), context.get(ContextTypes.ITEM_SEARCH)));
        SpaceDeposits deposits = context.sharedRuntime(SpaceDeposits.class, () -> new SpaceDeposits(context.engine(), context.get(ContextTypes.GAME)));
        ToolRegistry.Tool<SpaceInput, SpaceOutput> query = context.tool(BuiltinTools.SPACE_QUERY,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_QUERY, false, true, false, (execution, _) -> space.query(execution))
                        .forReasoning("List my system-space contents and their observed IDs without withdrawing anything."));
        ToolRegistry.Tool<SpaceTakeInput, SpaceOutput> take = context.tool(BuiltinTools.SPACE_TAKE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_TAKE, true, false, false, space::take));
        ToolRegistry.Tool<SpaceInventoryInput, SpaceInventoryOutput> inventory = context.tool(BuiltinTools.SPACE_INVENTORY,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_INVENTORY, false, true, false,
                        (execution, _) -> deposits.inventory(execution, context.id(), this.depositSlots)));
        ToolRegistry.Tool<SpaceDepositInput, SpaceInventoryOutput> deposit = context.tool(BuiltinTools.SPACE_DEPOSIT,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_DEPOSIT, true, false, false,
                        (execution, input) -> deposits.deposit(execution, input, context.id(), this.depositSlots)));
        ToolRegistry.Tool<RewardChoiceInput, RewardChoiceOutput> choose = context.tool(BuiltinTools.REWARD_CHOICE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.REWARD_CHOICE, true, true, false,
                        (execution, input) -> CompletableFuture.completedFuture(space.choice(execution, input))));
        return List.of(new SpaceModule(query, context.get(ContextTypes.COMMERCE)), new SpaceTakeModule(take),
                new SpaceInventoryModule(inventory), new SpaceDepositModule(deposit), new RewardChoiceModule(choose),
                new DeliverModule(context.tool(BuiltinTools.DELIVER, () -> new DeliverTool(space).tool()), space));
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        SpaceCommands.register(root);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                return data -> ModuleData.get(data, STORAGE).recoverDeliveries();
            }
        };
    }
}
