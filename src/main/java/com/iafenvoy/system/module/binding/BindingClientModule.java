package com.iafenvoy.system.module.binding;

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
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;


public final class BindingClientModule implements ClientModule {
    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(LiteralArgumentBuilder.<AssistantCommandSource>literal("grant")
                .then(RequiredArgumentBuilder.<AssistantCommandSource, Identifier>argument("system", IdentifierArgument.id())
                        .suggests((_, builder) -> {
                            ClientPacketListener connection = Minecraft.getInstance().getConnection();
                            if (connection != null)
                                SystemDataPackRegistries.ids(connection.registryAccess()).stream().map(Identifier::toString)
                                        .filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> grant(context.getArgument("system", Identifier.class), ""))
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("player", StringArgumentType.word())
                                .executes(context -> grant(context.getArgument("system", Identifier.class), StringArgumentType.getString(context, "player"))))));
        module.command(() -> AssistantClient.command("revoke"), "revoke");
        module.command(() -> AssistantClient.command("reset"), "reset");
    }

    private static int grant(Identifier system, String player) {
        AssistantClient.command("grant " + system + (player.isEmpty() ? "" : " " + player));
        return 1;
    }
}
