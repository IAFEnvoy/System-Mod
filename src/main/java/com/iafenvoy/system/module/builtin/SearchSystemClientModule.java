package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.builtin.input.QuestionInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class SearchSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("search", "nav_search", workspace -> {
            workspace.labels("nav_search", "web_search", "web_search_note");
            workspace.input("web_query", "system.ui.web_search_placeholder", "web_search");
            workspace.click("nav_search", () -> workspace.show("search"));
            workspace.click("web_search", () -> {
                String question = workspace.query("web_query");
                if (!question.isBlank()) AssistantClient.send(QuestionInput.online(question), question);
            });
        });
    }
}
