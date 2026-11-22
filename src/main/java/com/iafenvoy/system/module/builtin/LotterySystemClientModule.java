package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.LotteryQueryInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class LotterySystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        AgentCommand command = AgentCommand.argument("[query]", text -> new LotteryQueryInput(text, 0), "lottery", "抽奖");
        command.names().forEach(name -> module.command(command.node().apply(name)));
        module.workspace("lottery", "nav_lottery", workspace -> {
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
