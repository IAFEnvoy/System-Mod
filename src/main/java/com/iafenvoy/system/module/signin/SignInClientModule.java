package com.iafenvoy.system.module.signin;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantClientState;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;

public final class SignInClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("signin", "nav_signin", workspace -> {
            workspace.labels("nav_signin", "signin_title", "signin_text", "signin_action");
            workspace.click("nav_signin", () -> workspace.show("signin"));
            workspace.click("signin_action", () -> {
                workspace.show("chat");
                AssistantClient.send(SystemActionTypes.SIGN_IN_ACTION.get().of(EmptyInput.INSTANCE), "");
            });
        });
        module.cards().register(ResultTypes.SignInStatus.class)
                .state((previous, value) -> AssistantClientState.preferences(previous, value.historySaved(), value.externalModel()));
    }
}
