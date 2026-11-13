package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.assistant.tool.economy.BalanceTool;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.economy.BalanceModule;
import com.iafenvoy.system.module.builtin.economy.EconomyCommands;
import com.iafenvoy.system.module.builtin.storage.WalletDataStorage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class EconomySystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "economy");
    public static final MapCodec<EconomySystemModule> CODEC = MapCodec.unit(new EconomySystemModule());
    public static final ModuleStorage<WalletDataStorage> STORAGE = new ModuleStorage<>(ID, ID, WalletDataStorage.class, WalletDataStorage.CODEC, WalletDataStorage::new);

    @Override
    public MapCodec<EconomySystemModule> codec() {
        return CODEC;
    }

    private Wallets service(ModuleContext context) {
        return context.sharedRuntime(Wallets.class, () -> new Wallets(context.engine(), context.get(ContextTypes.GAME).server()));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return this.service(context);
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new BalanceModule(new BalanceTool(this.service(context)).tool()));
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        EconomyCommands.register(root);
    }
}
