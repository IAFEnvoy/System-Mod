package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.CoreModule;
import com.iafenvoy.system.module.ModuleCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class UsageSystemModule implements CoreModule {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("system", "usage");
    public static final MapCodec<UsageSystemModule> CODEC = MapCodec.unit(new UsageSystemModule());

    @Override
    public MapCodec<UsageSystemModule> codec() {
        return CODEC;
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("usage").executes(context -> open(context.getSource().getPlayerOrException(), false))
                .then(Commands.literal("all").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> open(context.getSource().getPlayerOrException(), true))));
    }

    private static int open(ServerPlayer player, boolean all) {
        AssistantServer runtime = ModuleCommands.runtime(player);
        if (runtime == null) return 0;
        runtime.usage().openUsage(player, all);
        return 1;
    }
}
