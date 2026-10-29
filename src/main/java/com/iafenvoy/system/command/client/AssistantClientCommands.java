package com.iafenvoy.system.command.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.command.AssistantCommandInput;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.util.CommandTrees;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Both assistant execution and game command registration consume the compiled client command cache.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClientCommands {
    private AssistantClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        // Initial client tag application precedes login and command packet merging in Minecraft 26.1.2.
        event.getDispatcher().getRoot().addChild(CommandTrees.copy(ClientModuleCaches.COMMAND.get().gameRoot()));
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
        } catch (CommandSyntaxException error) {
            AssistantClient.local(AgentResult.error("INVALID_INPUT"));
        }
        return true;
    }
}
