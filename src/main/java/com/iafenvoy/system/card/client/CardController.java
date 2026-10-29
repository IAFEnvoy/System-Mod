package com.iafenvoy.system.card.client;

import com.iafenvoy.system.agent.request.CardActionRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.CardAction;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.screen.AssistantMessages;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * One checked entry point serves template buttons, notification buttons and voice/number choices.
 */
public final class CardController {
    private CardController() {
    }

    public static void activate(CardRef source, CardButton button) {
        activate(source, button, false);
    }

    public static void activate(CardRef source, CardButton button, boolean notification) {
        if (!ClientCards.active(source) || button.action() instanceof CardAction.Server && AssistantClient.state().busy())
            return;
        if (notification && !AssistantMessages.actionStarted(source, button.behavior())) return;
        if (button.action() instanceof CardAction.Local(ResourceLocation id, Map<String, String> arguments)) {
            ClientModuleCaches.BINDINGS.get().localAction(id, source, arguments);
        } else if (button.action() instanceof CardAction.Server server)
            AssistantClient.send(new CardActionRequest(source, server),
                    notification && button.behavior() == CardButton.Behavior.REPLACE ? Optional.of(source) : Optional.empty());
    }
}
