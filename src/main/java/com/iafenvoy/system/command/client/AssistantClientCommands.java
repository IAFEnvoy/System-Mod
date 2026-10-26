package com.iafenvoy.system.command.client;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.runtime.client.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client navigation stays local; both execution and completion use these exact registered aliases. */
public final class AssistantClientCommands {
    private static final Pattern SELECT = Pattern.compile("^(?:/?select\\s+|/?选择\\s*)?([0-9]{1,4})$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Runnable> COMMANDS = new LinkedHashMap<>();

    static {
        register(() -> AssistantClient.open(true, ""), "mode simple", "simple", "简约模式");
        register(() -> AssistantClient.open(false, ""), "mode full", "full", "完整模式");
        register(() -> {
            if (AssistantClient.simpleMode()) AssistantClient.local(AgentResult.error("HISTORY_HIDDEN"));
            else AssistantClient.send(AgentRequest.Control.HISTORY);
        }, "history", "对话历史");
        register(() -> AssistantClient.send(AgentRequest.Control.FORGET), "forget", "清空记忆");
        register(() -> preferences(true, AssistantClient.externalModel()), "memory on");
        register(() -> preferences(false, AssistantClient.externalModel()), "memory off");
        register(() -> preferences(AssistantClient.saveHistory(), true), "ai on");
        register(() -> preferences(AssistantClient.saveHistory(), false), "ai off");
        register(() -> AssistantClient.command("grant"), "grant");
        register(() -> AssistantClient.command("revoke"), "revoke");
        register(() -> AssistantClient.command("usage"), "usage", "token统计", "token 统计", "用量统计");
        register(() -> AssistantClient.command("usage all"), "usage all");
        register(() -> settings(AssistantSettingsScreen.Tab.GENERAL), "settings", "设置", "系统设置");
        register(() -> settings(AssistantSettingsScreen.Tab.TTS), "tts", "voice", "语音设置");
        register(() -> settings(AssistantSettingsScreen.Tab.ASR), "asr", "语音识别设置");
        register(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null) screen.onClose();
        }, "close", "关闭界面");
        register(() -> {
            if (Minecraft.getInstance().screen instanceof AssistantInputScreen screen) screen.recognitionCommand();
        }, "listen", "语音输入");
    }

    private AssistantClientCommands() {
    }

    private static void register(Runnable handler, String... aliases) {
        for (String alias : aliases)
            if (COMMANDS.putIfAbsent(alias, handler) != null) throw new IllegalArgumentException("Duplicate client command " + alias);
    }

    public static List<String> suggestions() {
        List<String> result = new ArrayList<>(COMMANDS.keySet());
        result.add("select ");
        result.add("选择 ");
        return List.copyOf(result);
    }

    public static boolean execute(String text) {
        String command = text.strip();
        if (command.regionMatches(true, 0, "/system ", 0, 8)) command = command.substring(8).strip();
        Matcher selection = SELECT.matcher(command);
        if (selection.matches()) {
            int index = Integer.parseInt(selection.group(1)) - 1;
            List<AgentResult.Action> actions = AssistantClient.currentActions();
            if (index < 0 || index >= actions.size()) AssistantClient.local(AgentResult.error("INVALID_SELECTION"));
            else AssistantClient.send(actions.get(index), text);
            return true;
        }
        if (command.startsWith("/")) command = command.substring(1);
        Runnable handler = COMMANDS.get(command.toLowerCase(Locale.ROOT));
        if (handler == null) return false;
        handler.run();
        return true;
    }

    private static void settings(AssistantSettingsScreen.Tab tab) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new AssistantSettingsScreen(minecraft.screen, tab));
    }

    private static void preferences(boolean history, boolean external) {
        AssistantClient.send(new AgentRequest.Preferences(history, external));
    }
}
