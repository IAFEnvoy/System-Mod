package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class ConversationClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> {
            if (AssistantClient.simpleMode()) AssistantClient.local(AgentResult.error(AgentFailure.HISTORY_HIDDEN));
            else AssistantClient.send(ControlRequest.HISTORY);
        }, "history", "对话历史");
        module.command(() -> AssistantClient.send(ControlRequest.FORGET), "forget", "清空记忆");
        module.command(() -> preferences(true), "memory on");
        module.command(() -> preferences(false), "memory off");
        module.workspace("", "", workspace -> {
            workspace.click("forget", () -> AssistantClient.send(ControlRequest.FORGET));
            workspace.click("history", () -> {
                workspace.show("chat");
                AssistantClient.send(ControlRequest.HISTORY);
            });
        });
    }

    private static void preferences(boolean history) {
        AssistantClient.send(new PreferencesRequest(history, AssistantClient.state().preferredMode()));
    }
}
