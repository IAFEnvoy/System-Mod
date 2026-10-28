package com.iafenvoy.system.module.core;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

public final class CoreClientModule implements ClientModule {
    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> AssistantClient.open(true, ""), "mode simple", "simple", "简约模式");
        module.command(() -> AssistantClient.open(false, ""), "mode full", "full", "完整模式");
        module.command(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) screen.onClose();
        }, "close", "关闭界面");
        module.pattern(CoreClientModule::select, "select ", "选择 ");
        module.workspace("chat", "nav_chat", WorkspaceGroup.CHAT, workspace -> {
            workspace.labels("nav_chat");
            workspace.click("nav_chat", () -> workspace.show("chat"));
        });
        module.cards().state(ResultTypes.ClientState.class, (_, state) -> state);
        module.speech("system.result.help", _ -> tr("system.tts.message.help"));
    }

    private static final Pattern SELECT = Pattern.compile("^(?:select\\s+|选择\\s*)?([0-9]{1,4})$", Pattern.CASE_INSENSITIVE);

    private static boolean select(String command) {
        Matcher match = SELECT.matcher(command);
        if (!match.matches()) return false;
        int index = Integer.parseInt(match.group(1)) - 1;
        List<AgentResult.Action> actions = AssistantClient.state().currentActions();
        if (index < 0 || index >= actions.size()) AssistantClient.local(AgentResult.error("INVALID_SELECTION"));
        else AssistantClient.send(actions.get(index), command);
        return true;
    }
}
