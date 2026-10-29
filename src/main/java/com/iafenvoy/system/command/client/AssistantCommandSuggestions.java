package com.iafenvoy.system.command.client;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandInput;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        // Local candidates remain usable even when a new vanilla suggestion request cancels an older one.
        local.thenAccept(receive);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || connection.getCommands().getRoot().getChild("system") == null) return;
        String serverInput = "system " + draft.substring(input.start());
        // Use the received permission-filtered tree and its native server suggestion providers.
        // 1.21.1 types the received tree against SharedSuggestionProvider, matching getSuggestionsProvider().
        CommandDispatcher<SharedSuggestionProvider> server = connection.getCommands();
        CompletableFuture<Suggestions> remote = server.getCompletionSuggestions(server.parse(new AssistantCommandInput(serverInput, 0, false).reader(server.getRoot()), connection.getSuggestionsProvider()));
        Set<String> workflows = AssistantClient.state().commands().stream().map(String::strip).collect(Collectors.toSet());
        local.thenCombine(remote, (result, serverResult) -> {
            Map<String, Suggestion> candidates = new LinkedHashMap<>();
            result.forEach(suggestion -> candidates.put(suggestion.value(), suggestion));
            for (com.mojang.brigadier.suggestion.Suggestion suggestion : serverResult.getList()) {
                String applied = suggestion.apply(serverInput);
                if (!applied.startsWith("system ")) continue;
                String value = draft.substring(0, input.start()) + applied.substring(7);
                if (!supported(value, dispatcher, source, workflows)) continue;
                String hint = suggestion.getTooltip() == null ? "" : suggestion.getTooltip().getString();
                if (!value.equals(draft)) candidates.putIfAbsent(value, new Suggestion(value, hint));
            }
            return List.copyOf(candidates.values());
        }).thenAccept(receive);
    }

    private static boolean supported(String value, CommandDispatcher<AssistantCommandSource> dispatcher,
                                     AssistantCommandSource source, Set<String> workflows) {
        AssistantCommandInput input = AssistantCommandInput.of(value);
        if (workflows.contains(input.name().toLowerCase(Locale.ROOT))) return true;
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
