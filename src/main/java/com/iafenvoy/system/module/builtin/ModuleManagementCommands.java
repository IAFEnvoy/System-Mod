package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.SystemModule;
import com.iafenvoy.system.module.CoreModule;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        root.then(Commands.literal("module").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(change("add", true))
                .then(change("remove", false))
                .then(Commands.literal("list").executes(context -> list(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> list(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, boolean add) {
        return Commands.literal(name).then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("module", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                SystemDataPackRegistries.modules(context.getSource().registryAccess()).stream()
                                        .flatMap(registry -> registry.keySet().stream().filter(id -> {
                                            SystemModule module = registry.get(id);
                                            return module.available() && module.server().filter(CoreModule.class::isInstance).isEmpty();
                                        })).sorted(), builder))
                        .executes(context -> change(context, add))));
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation id = ResourceLocationArgument.getId(context, "module");
        SystemModule module = SystemDataPackRegistries.modules(source.registryAccess()).map(registry -> registry.get(id)).orElse(null);
        if (module == null || !module.available()) {
            source.sendFailure(Component.translatable("system.command.module_unknown", id.toString()));
            return 0;
        }
        if (module.server().filter(CoreModule.class::isInstance).isPresent()) {
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
        List<ResourceLocation> modules = SystemDataPackRegistries.moduleIds(source.registryAccess());
        source.sendSuccess(() -> Component.translatable("system.command.module_list", modules.size(), names(modules)), false);
        return modules.size();
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null || !requireSystem(source, player, runtime.moduleManager())) return 0;
        List<ResourceLocation> modules = runtime.moduleManager().enabled(player.getUUID());
        source.sendSuccess(() -> Component.translatable("system.command.module_player_list", player.getName(), modules.size(), names(modules)), false);
        return modules.size();
    }

    private static Component names(List<ResourceLocation> modules) {
        return modules.isEmpty() ? Component.translatable("system.command.module_empty")
                : Component.literal(modules.stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
    }

    private static boolean requireSystem(CommandSourceStack source, ServerPlayer player, ModuleManager modules) {
        if (modules.hasSystem(player.getUUID())) return true;
        source.sendFailure(Component.translatable("system.command.feedback", player.getName(), Component.translatable("system.error.not_bound")));
        return false;
    }
}
