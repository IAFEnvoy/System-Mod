package com.iafenvoy.system.module.player;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class PlayerClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_player", workspace -> {
            workspace.labels("nav_player");
            workspace.click("nav_player", () -> {
                workspace.show("chat");
                AssistantClient.send(ResultTypes.PLAYER_ACTION.of(EmptyInput.INSTANCE), "");
            });
        });
        module.cards().register(PlayerSnapshotOutput.class, (builder, value) -> builder
                .put("name", value.name()).put("health", value.health()).put("max_health", value.maxHealth())
                .put("experience_level", value.experienceLevel()).put("dimension", value.dimension())
                .put("language", value.language()).list("onlinePlayers", value.onlinePlayers(),
                        (prefix, name) -> builder.put(prefix + "player_name", name)));
    }
}
