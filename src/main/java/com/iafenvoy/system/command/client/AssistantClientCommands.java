package com.iafenvoy.system.command.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client navigation stays local; both execution and completion use these exact registered aliases.
 */
@EventBusSubscriber(Dist.CLIENT)
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
        register(() -> preferences(true, AssistantClient.state().externalModel()), "memory on");
        register(() -> preferences(false, AssistantClient.state().externalModel()), "memory off");
        register(() -> preferences(AssistantClient.state().saveHistory(), true), "ai on");
        register(() -> preferences(AssistantClient.state().saveHistory(), false), "ai off");
        register(() -> AssistantClient.command("grant"), "grant");
        register(() -> AssistantClient.command("revoke"), "revoke");
        register(() -> AssistantClient.command("reset"), "reset");
        register(() -> AssistantClient.command("usage"), "usage", "token统计", "token 统计", "用量统计");
        register(() -> AssistantClient.command("usage all"), "usage all");
        register(AssistantClientCommands::settings, "settings", "设置", "系统设置");
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

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SystemMod.MOD_ID)
                .then(Commands.literal("settings").executes(context -> {
                    settings();
                    return 1;
                })));
    }

    private static void register(Runnable handler, String... aliases) {
        for (String alias : aliases)
            if (COMMANDS.putIfAbsent(alias, handler) != null)
                throw new IllegalArgumentException("Duplicate client command " + alias);
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
            List<AgentResult.Action> actions = AssistantClient.state().currentActions();
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

    private static void settings() {
        Minecraft minecraft = Minecraft.getInstance();
        // A chat command returns to gameplay; assistant navigation keeps its original page and draft.
        Screen parent = minecraft.screen instanceof ChatScreen ? null : minecraft.screen;
        minecraft.setScreen(new AssistantSettingsScreen(parent, AssistantSettingsScreen.Tab.GENERAL));
    }

    private static void preferences(boolean history, boolean external) {
        AssistantClient.send(new AgentRequest.Preferences(history, external));
    }
}
