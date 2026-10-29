package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.CoreModule;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Administrator commands only edit overrides; ModuleManager owns activation, persistence and client sync.
 */
final class ModuleManagementCommands {
    private ModuleManagementCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("module").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(change("add", true))
                .then(change("remove", false))
                .then(Commands.literal("list").executes(context -> list(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> list(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, boolean add) {
        return Commands.literal(name).then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("module", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                SystemRegistries.SERVER_MODULE.stream().filter(ServerModule::available)
                                        .filter(module -> !(module instanceof CoreModule)).map(SystemRegistries.SERVER_MODULE::getKey).sorted(), builder))
                        .executes(context -> change(context, add))));
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Identifier id = IdentifierArgument.getId(context, "module");
        ServerModule module = SystemRegistries.SERVER_MODULE.getValue(id);
        if (module == null || !module.available()) {
            source.sendFailure(Component.translatable("system.command.module_unknown", id.toString()));
            return 0;
        }
        if (module instanceof CoreModule) {
            source.sendFailure(Component.translatable("system.command.module_required", id.toString()));
            return 0;
        }
        ModuleManager modules = runtime.moduleManager();
        if (!requireSystem(source, player, modules)) return 0;
        // Record the override even if a data-pack condition already produces the requested effective state.
        boolean changed = add ? modules.add(player, id) : modules.remove(player, id);
        if (!changed) {
            source.sendFailure(Component.translatable("system.command.module_unchanged", id.toString(), player.getName()));
            return 0;
        }
        String key = add ? "system.command.module_added" : "system.command.module_removed";
        source.sendSuccess(() -> Component.translatable(key, id.toString(), player.getName()), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        List<Identifier> modules = SystemRegistries.SERVER_MODULE.keySet().stream().sorted().toList();
        source.sendSuccess(() -> Component.translatable("system.command.module_list", modules.size(), names(modules)), false);
        return modules.size();
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null || !requireSystem(source, player, runtime.moduleManager())) return 0;
        List<Identifier> modules = runtime.moduleManager().enabled(player.getUUID());
        source.sendSuccess(() -> Component.translatable("system.command.module_player_list", player.getName(), modules.size(), names(modules)), false);
        return modules.size();
    }

    private static Component names(List<Identifier> modules) {
        return modules.isEmpty() ? Component.translatable("system.command.module_empty")
                : Component.literal(modules.stream().map(Identifier::toString).collect(Collectors.joining(", ")));
    }

    private static boolean requireSystem(CommandSourceStack source, ServerPlayer player, ModuleManager modules) {
        if (modules.hasSystem(player.getUUID())) return true;
        source.sendFailure(Component.translatable("system.command.feedback", player.getName(), Component.translatable("system.error.not_bound")));
        return false;
    }
}
