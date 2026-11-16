package com.iafenvoy.system.command.client;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandInput;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Providers read only current trusted results; the popup consumes Brigadier replacement ranges.
 */
public final class AssistantCommandSuggestions {
    private AssistantCommandSuggestions() {
    }

    public record Suggestion(String value, String hint) {
        public String label() {
            return this.hint.isBlank() ? this.value : this.value + " · " + this.hint;
        }
    }

    public static void find(String draft, Consumer<List<Suggestion>> receive) {
        if (draft.isBlank()) {
            receive.accept(List.of());
            return;
        }
        if (draft.stripLeading().equalsIgnoreCase("/system")) {
            find(draft + " ", receive);
            return;
        }
        AssistantCommandInput input = AssistantCommandInput.of(draft);
        AssistantCommandSource source = new AssistantCommandSource(draft, AssistantClient.state()::hasModule);
        CommandDispatcher<AssistantCommandSource> dispatcher = ClientModuleCaches.COMMAND.get().suggestions(source);
        CompletableFuture<List<Suggestion>> local = dispatcher.getCompletionSuggestions(dispatcher.parse(input.reader(dispatcher.getRoot()), source))
                .thenApply(result -> values(result, draft));
        // Local candidates remain usable even when a newer server suggestion request cancels an older one.
        local.thenAccept(receive);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        CommandNode<ClientSuggestionProvider> gameRoot = connection.getCommands().getRoot().getChild(SystemMod.MOD_ID);
        CompletableFuture<List<Suggestion>> remote;
        if (gameRoot != null && gameRoot.getChild(input.name().toLowerCase(Locale.ROOT)) != null
                && dispatcher.getRoot().getChild(input.name().toLowerCase(Locale.ROOT)) != null) {
            // Explicit management bridges may still reuse vanilla player/registry completions.
            String serverInput = "system " + draft.substring(input.start());
            CommandDispatcher<ClientSuggestionProvider> server = connection.getCommands();
            remote = server.getCompletionSuggestions(server.parse(new AssistantCommandInput(serverInput, 0, false)
                            .reader(server.getRoot()), connection.getSuggestionsProvider()))
                    .thenApply(result -> result.getList().stream()
                            .filter(suggestion -> suggestion.apply(serverInput).startsWith("system "))
                            .map(suggestion -> {
                                String applied = suggestion.apply(serverInput);
                                String value = draft.substring(0, input.start()) + applied.substring(7);
                                return new Suggestion(value, suggestion.getTooltip() == null ? "" : suggestion.getTooltip().getString());
                            }).filter(suggestion -> !suggestion.value().equals(draft)
                                    && supported(suggestion.value(), dispatcher, source)).toList());
        } else {
            // Workflow arguments remain private; the server completes them without publishing game command nodes.
            remote = ClientModuleCaches.COMMAND.remoteSuggestions(draft).thenApply(result -> result.stream()
                    .map(suggestion -> new Suggestion(suggestion.value(), suggestion.hint()))
                    .filter(suggestion -> !suggestion.value().equals(draft)).toList());
        }
        local.thenCombine(remote, (result, extra) -> {
            Map<String, Suggestion> candidates = new LinkedHashMap<>();
            result.forEach(suggestion -> candidates.put(suggestion.value(), suggestion));
            extra.forEach(suggestion -> candidates.putIfAbsent(suggestion.value(), suggestion));
            return List.copyOf(candidates.values());
        }).thenAccept(receive);
    }

    private static boolean supported(String value, CommandDispatcher<AssistantCommandSource> dispatcher,
                                     AssistantCommandSource source) {
        AssistantCommandInput input = AssistantCommandInput.of(value);
        // Do not advertise platform commands which the assistant has no execution path for.
        ParseResults<AssistantCommandSource> parsed = dispatcher.parse(input.reader(dispatcher.getRoot()), source);
        return !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead();
    }

    private static List<Suggestion> values(Suggestions suggestions, String input) {
        return suggestions.getList().stream().map(suggestion -> new Suggestion(suggestion.apply(input),
                        suggestion.getTooltip() == null ? "" : suggestion.getTooltip().getString()))
                .filter(suggestion -> !suggestion.value().equals(input)).toList();
    }

    public static void suggest(SuggestionsBuilder builder, String value, String hint) {
        value = value.replace('\r', ' ').replace('\n', ' ');
        if (value.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
            builder.suggest(value, new LiteralMessage(MiscUtil.clip(hint.replace('\r', ' ').replace('\n', ' '), 96)));
    }
}
