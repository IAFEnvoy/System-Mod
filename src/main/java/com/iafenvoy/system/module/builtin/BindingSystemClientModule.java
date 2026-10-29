package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

public final class BindingSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.command(LiteralArgumentBuilder.<AssistantCommandSource>literal("grant")
                .then(RequiredArgumentBuilder.<AssistantCommandSource, ResourceLocation>argument("system", ResourceLocationArgument.id())
                        .suggests((unused1, builder) -> {
                            ClientPacketListener connection = Minecraft.getInstance().getConnection();
                            if (connection != null)
                                SystemDataPackRegistries.ids(connection.registryAccess()).stream().map(ResourceLocation::toString)
                                        .filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> grant(context.getArgument("system", ResourceLocation.class), ""))
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("player", StringArgumentType.word())
                                .executes(context -> grant(context.getArgument("system", ResourceLocation.class), StringArgumentType.getString(context, "player"))))));
        module.command(() -> AssistantClient.command("revoke"), "revoke");
        module.command(() -> AssistantClient.command("reset"), "reset");
    }

    private static int grant(ResourceLocation system, String player) {
        AssistantClient.command("grant " + system + (player.isEmpty() ? "" : " " + player));
        return 1;
    }
}
