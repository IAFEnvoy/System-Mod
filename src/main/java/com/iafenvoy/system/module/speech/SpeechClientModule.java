package com.iafenvoy.system.module.speech;

import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class SpeechClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("system", "speech");
    }

    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> {
            if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.recognitionCommand();
        }, "listen", "语音输入");
    }

}
