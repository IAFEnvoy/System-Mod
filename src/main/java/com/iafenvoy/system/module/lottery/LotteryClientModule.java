package com.iafenvoy.system.module.lottery;

import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class LotteryClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("lottery", "nav_lottery", workspace -> {
            workspace.labels("nav_lottery", "lottery_search", "lottery_refresh", "lottery_text");
            workspace.input("lottery_query", "system.ui.lottery_placeholder", "lottery_search");
            workspace.click("nav_lottery", () -> {
                workspace.show("lottery");
                AssistantClient.send(new LotteryQueryInput("", 0), "");
            });
            workspace.click("lottery_refresh", () -> AssistantClient.send(new LotteryQueryInput("", 0), ""));
            workspace.click("lottery_search", () -> AssistantClient.send(new LotteryQueryInput(workspace.query("lottery_query"), 0), ""));
        });
    }
}
