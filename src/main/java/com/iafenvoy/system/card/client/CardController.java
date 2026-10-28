package com.iafenvoy.system.card.client;

import com.iafenvoy.system.agent.request.CardActionRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.CardAction;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.module.client.ClientModuleBindings;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One checked entry point serves template buttons, notification buttons and voice/number choices.
 */
public final class CardController {
    private static final Map<Identifier, BiConsumer<CardRef, Map<String, String>>> LOCAL = new LinkedHashMap<>();

    static {
        register(Identifier.parse("system:close"), (source, _) -> AssistantMessages.dismissCard(source.id()));
        register(Identifier.parse("system:open"), (_, _) -> AssistantClient.open(AssistantClient.simpleMode(), ""));
        register(Identifier.parse("system:settings"), (_, _) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AssistantSettingsScreen(minecraft.screen, AssistantSettingsScreen.Tab.GENERAL));
        });
        register(Identifier.parse("system:workspace"), (_, arguments) -> {
            AssistantClient.open(false, "");
            if (Minecraft.getInstance().screen instanceof AssistantScreen screen)
                screen.showPage(arguments.getOrDefault("page", "chat"));
        });
    }

    private CardController() {
    }

    public static void register(Identifier id, BiConsumer<CardRef, Map<String, String>> handler) {
        if (LOCAL.putIfAbsent(id, handler) != null)
            throw new IllegalArgumentException("Duplicate local card action " + id);
    }

    public static void activate(CardRef source, CardButton button) {
        ClientModuleBindings.get();
        if (!ClientCards.active(source) || button.action() instanceof CardAction.Server && AssistantClient.state().busy())
            return;
        AssistantMessages.actionStarted(source, button.behavior());
        if (button.action() instanceof CardAction.Local(Identifier id, Map<String, String> arguments)) {
            BiConsumer<CardRef, Map<String, String>> handler = LOCAL.get(id);
            if (handler != null) handler.accept(source, arguments);
        } else if (button.action() instanceof CardAction.Server server)
            AssistantClient.send(new CardActionRequest(source, server));
    }
}
