package com.iafenvoy.system.command.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.emotion.EmotionDefinition;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.iafenvoy.system.screen.emotion.EmotionHudState;
import com.iafenvoy.system.screen.emotion.EmotionProfile;
import com.iafenvoy.system.screen.emotion.EmotionProfiles;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Explicit client debug entry in game chat and assistant input; changes stay local and require no administrator permission.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class EmotionCommands {
    private EmotionCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SystemMod.MOD_ID).then(node()));
    }

    public static <S> LiteralArgumentBuilder<S> node() {
        return LiteralArgumentBuilder.<S>literal("emotion")
                .executes(_ -> feedback("system.emotion.status", EmotionHudState.machine().profileId().toString(), EmotionHudState.machine().state()))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(_ -> feedback("system.emotion.list",
                        definitions().entrySet().stream().sorted(Map.Entry.comparingByKey())
                                .map(entry -> entry.getKey() + " · " + entry.getValue().name()).collect(Collectors.joining(", ")))))
                .then(LiteralArgumentBuilder.<S>literal("set")
                        .then(RequiredArgumentBuilder.<S, Identifier>argument("state", IdentifierArgument.id())
                                .suggests((_, builder) -> {
                                    definitions().forEach((id, definition) -> AssistantCommandSuggestions.suggest(builder, id.toString(), definition.name()));
                                    return builder.buildFuture();
                                }).executes(context -> {
                                    Identifier state = context.getArgument("state", Identifier.class);
                                    if (!definitions().containsKey(state))
                                        return feedback("system.emotion.unknown", state.toString());
                                    if (!EmotionHudState.machine().setState(state.toString())) {
                                        EmotionProfiles.unknown(EmotionHudState.machine().profileId(), state.toString());
                                        EmotionHudState.machine().setState("");
                                    }
                                    EmotionHudState.shown(true);
                                    return feedback("system.emotion.selected", state.toString());
                                })))
                .then(LiteralArgumentBuilder.<S>literal("speaking")
                        .then(LiteralArgumentBuilder.<S>literal("true").executes(_ -> speaking(true)))
                        .then(LiteralArgumentBuilder.<S>literal("false").executes(_ -> speaking(false)))
                        .then(LiteralArgumentBuilder.<S>literal("auto").executes(_ -> {
                            EmotionHudState.machine().setSpeaking(null);
                            return feedback("system.emotion.speaking_auto");
                        })))
                .then(LiteralArgumentBuilder.<S>literal("show")
                        .then(RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool()).executes(context -> {
                            boolean shown = BoolArgumentType.getBool(context, "enabled");
                            EmotionHudState.shown(shown);
                            return feedback(shown ? "system.emotion.shown" : "system.emotion.hidden");
                        })))
                .then(LiteralArgumentBuilder.<S>literal("profile")
                        .then(RequiredArgumentBuilder.<S, Identifier>argument("profile", IdentifierArgument.id())
                                .suggests((_, builder) -> {
                                    EmotionProfiles.all().keySet().forEach(id -> AssistantCommandSuggestions.suggest(builder, id.toString(), ""));
                                    return builder.buildFuture();
                                }).executes(context -> {
                                    Identifier profile = context.getArgument("profile", Identifier.class);
                                    if (!EmotionHudState.machine().setProfile(profile))
                                        return feedback("system.emotion.unknown", profile.toString());
                                    EmotionHudState.shown(true);
                                    return feedback("system.emotion.profile", profile.toString());
                                })))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(_ -> {
                    EmotionHudState.clear();
                    return feedback("system.emotion.cleared");
                }))
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(_ -> {
                    EmotionHudState.clear();
                    EmotionHudState.shown(true);
                    return feedback("system.emotion.reset");
                }));
    }

    private static int speaking(boolean enabled) {
        EmotionHudState.machine().setSpeaking(enabled);
        EmotionHudState.shown(true);
        return feedback(enabled ? "system.emotion.speaking_on" : "system.emotion.speaking_off");
    }

    private static Map<Identifier, EmotionDefinition> definitions() {
        Map<Identifier, EmotionDefinition> definitions = new LinkedHashMap<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.registryAccess().lookupOrThrow(SystemResourceKeys.EMOTION).entrySet()
                .forEach(entry -> definitions.put(entry.getKey().identifier(), entry.getValue()));
        EmotionProfile profile = EmotionProfiles.get(EmotionHudState.machine().profileId());
        if (profile != null) profile.states().keySet().forEach(state -> {
            Identifier id = Identifier.tryParse(state);
            if (id != null) definitions.putIfAbsent(id, new EmotionDefinition(state, "", "", 0));
        });
        return definitions;
    }

    private static int feedback(String key, String... values) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.translatable(key, (Object[]) values));
        return 1;
    }
}
