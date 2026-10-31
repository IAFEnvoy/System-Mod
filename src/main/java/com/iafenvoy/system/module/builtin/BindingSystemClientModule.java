package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class BindingSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.command(LiteralArgumentBuilder.<AssistantCommandSource>literal("grant")
                .then(RequiredArgumentBuilder.<AssistantCommandSource, Identifier>argument("system", IdentifierArgument.id())
                        .suggests((_, builder) -> SharedSuggestionProvider.suggestResource(Optional.of(Minecraft.getInstance())
                                .stream().map(Minecraft::getConnection).filter(Objects::nonNull).map(ClientPacketListener::registryAccess)
                                .map(x -> x.lookupOrThrow(SystemResourceKeys.SYSTEM)).map(Registry::keySet).flatMap(Collection::stream), builder))
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
