package com.iafenvoy.system.module.conversation;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

public final class ConversationClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("system", "conversation");
    }

    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> {
            if (AssistantClient.simpleMode()) AssistantClient.local(AgentResult.error("HISTORY_HIDDEN"));
            else AssistantClient.send(AgentRequest.Control.HISTORY);
        }, "history", "对话历史");
        module.command(() -> AssistantClient.send(AgentRequest.Control.FORGET), "forget", "清空记忆");
        module.command(() -> preferences(true, AssistantClient.state().externalModel()), "memory on");
        module.command(() -> preferences(false, AssistantClient.state().externalModel()), "memory off");
        module.command(() -> preferences(AssistantClient.state().saveHistory(), true), "ai on");
        module.command(() -> preferences(AssistantClient.state().saveHistory(), false), "ai off");
        module.workspace("", "", workspace -> {
            workspace.labels("history", "forget");
            workspace.click("forget", () -> AssistantClient.send(AgentRequest.Control.FORGET));
            workspace.click("history", () -> {
                workspace.show("chat");
                AssistantClient.send(AgentRequest.Control.HISTORY);
            });
        });
        module.cards().register(ResultTypes.Preferences.class, (builder, value) -> builder
                .put("history_saved", value.historySaved()).put("external_model", value.externalModel()));
        module.cards().state(ResultTypes.Preferences.class, (previous, value) -> AssistantClientState.preferences(previous, value.historySaved(), value.externalModel()));
        module.speech("system.result.history", _ -> "");
        module.speech("system.result.state", _ -> "");
    }

    private static void preferences(boolean history, boolean external) {
        AssistantClient.send(new AgentRequest.Preferences(history, external));
    }
}
