package com.iafenvoy.system.module.conversation;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class ConversationClientModule implements ClientModule {
    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> {
            if (AssistantClient.simpleMode()) AssistantClient.local(AgentResult.error("HISTORY_HIDDEN"));
            else AssistantClient.send(ControlRequest.HISTORY);
        }, "history", "对话历史");
        module.command(() -> AssistantClient.send(ControlRequest.FORGET), "forget", "清空记忆");
        module.command(() -> preferences(true, AssistantClient.state().externalModel()), "memory on");
        module.command(() -> preferences(false, AssistantClient.state().externalModel()), "memory off");
        module.command(() -> preferences(AssistantClient.state().saveHistory(), true), "ai on");
        module.command(() -> preferences(AssistantClient.state().saveHistory(), false), "ai off");
        module.workspace("", "", workspace -> {
            workspace.labels("history", "forget");
            workspace.click("forget", () -> AssistantClient.send(ControlRequest.FORGET));
            workspace.click("history", () -> {
                workspace.show("chat");
                AssistantClient.send(ControlRequest.HISTORY);
            });
        });
    }

    private static void preferences(boolean history, boolean external) {
        AssistantClient.send(new PreferencesRequest(history, external));
    }
}
