package com.iafenvoy.system.module.binding;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.screen.AssistantMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

public final class BindingClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("system", "binding");
    }

    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> AssistantClient.command("grant"), "grant");
        module.command(() -> AssistantClient.command("revoke"), "revoke");
        module.command(() -> AssistantClient.command("reset"), "reset");
        module.speech("system.result.bound", _ -> "");
    }

    public static void bound(String playerName) {
        Component name = Component.translatable(AssistantClient.system().name());
        Component description = Component.translatable(AssistantClient.system().description());
        AssistantMessages.post(new AssistantMessages.Message(tr("system.ui.assistant"),
                Component.translatable("system.ui.binding_text", playerName, name, description).getString(),
                Component.translatable("system.tts.message.binding", name, description).getString(), List.of(
                new AssistantMessages.Button("open", "system.ui.message_open_button", false,
                        () -> AssistantClient.open(AssistantClient.simpleMode(), ""))), 240));
    }

}
