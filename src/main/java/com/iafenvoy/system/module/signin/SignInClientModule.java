package com.iafenvoy.system.module.signin;

import com.iafenvoy.system.agent.builtin.input.SignInInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class SignInClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("signin", "nav_signin", workspace -> {
            workspace.labels("nav_signin", "signin_title", "signin_text", "signin_action");
            workspace.click("nav_signin", () -> workspace.show("signin"));
            workspace.click("signin_action", () -> {
                workspace.show("chat");
                AssistantClient.send(SignInInput.INSTANCE, "");
            });
        });
    }
}
