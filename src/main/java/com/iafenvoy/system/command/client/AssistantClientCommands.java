package com.iafenvoy.system.command.client;

import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandInput;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Assistant input consumes the private command cache; no module nodes enter the game command tree.
 */
public final class AssistantClientCommands {
    private AssistantClientCommands() {
    }

    public static boolean execute(String text) {
        CommandDispatcher<AssistantCommandSource> dispatcher = ClientModuleCaches.COMMAND.get().dispatcher();
        AssistantCommandInput input = AssistantCommandInput.of(text.stripTrailing());
        AssistantCommandSource source = new AssistantCommandSource(input.text(), AssistantClient.state()::hasModule);
        ParseResults<AssistantCommandSource> parsed = dispatcher.parse(input.reader(dispatcher.getRoot()), source);
        if (parsed.getContext().getNodes().isEmpty()) return false;
        // Invalid ordinary prose must still reach semantic routing; slash commands report native syntax errors.
        if (!input.explicit() && (parsed.getReader().canRead() || parsed.getContext().getCommand() == null))
            return false;
        try {
            dispatcher.execute(parsed);
            if (source.action() != null) AssistantClient.send(source.action(), text.strip());
        } catch (CommandSyntaxException error) {
            AssistantClient.local(AgentResult.error(AgentFailure.INVALID_INPUT));
        }
        return true;
    }
}
