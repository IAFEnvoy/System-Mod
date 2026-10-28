package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;


public final class QuestClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("quests", "nav_quests", workspace -> {
            Runnable refresh = () -> AssistantClient.send(SystemActionTypes.QUESTS_ACTION.get().of(new QuestQueryInput("", false)), "");
            workspace.labels("nav_quests", "quest_refresh", "quest_claimable");
            workspace.click("nav_quests", () -> {
                workspace.show("quests");
                refresh.run();
            });
            workspace.click("quest_refresh", refresh);
            workspace.click("quest_claimable", () -> AssistantClient.send(SystemActionTypes.QUESTS_ACTION.get().of(new QuestQueryInput("", true)), ""));
        });
    }
}
