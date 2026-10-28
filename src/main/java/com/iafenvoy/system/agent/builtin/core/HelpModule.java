package com.iafenvoy.system.agent.builtin.core;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.HelpInput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.data.SelectionCardData;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Returns the supported command summary without invoking a tool.
 */
public final class HelpModule extends BuiltinModule<HelpInput> {
    public HelpModule() {
        super(HelpInput.CODEC, "Show supported commands and usage.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<HelpInput> routing() {
        return ModuleRouting.<HelpInput>defaults()
                .button(_ -> HelpInput.INSTANCE)
                .command(AgentCommand.literal(HelpInput.INSTANCE, "help", "帮助"))
                .semantic(RoutingProfile.read(text -> HelpInput.INSTANCE,
                        List.of("我能用哪些功能", "告诉我怎么使用系统", "查看系统指令手册", "系统有什么功能"),
                        List.of("What can this system do?", "Show the command manual", "How do I use the system?", "Show available capabilities")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, HelpInput input) {
        SelectionCardData shortcuts = SelectionCardData.shortcuts("system.result.help", "system.tts.message.help", context.shortcuts());
        return AgentResult.data("system.result.help", new SelectionCardData(
                Component.translatable("system.result.help").append("\n").append(String.join("\n", context.commandUsages())),
                shortcuts.speech(), shortcuts.choices()));
    }
}
