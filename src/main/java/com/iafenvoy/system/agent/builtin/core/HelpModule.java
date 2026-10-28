package com.iafenvoy.system.agent.builtin.core;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.data.SelectionCardData;
import com.iafenvoy.system.registry.SystemActionTypes;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Returns the supported command summary without invoking a tool.
 */
public final class HelpModule extends BuiltinModule<EmptyInput> {
    public HelpModule() {
        super(SystemActionTypes.HELP_ACTION.get(), "Show supported commands and usage.",
                EmptyInput.CODEC, List.of(), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<EmptyInput> routing() {
        return ModuleRouting.<EmptyInput>defaults()
                .button(_ -> EmptyInput.INSTANCE)
                .command(AgentCommand.literal(EmptyInput.INSTANCE, "help", "帮助"))
                .semantic(RoutingProfile.read(text -> EmptyInput.INSTANCE,
                        List.of("我能用哪些功能", "告诉我怎么使用系统", "查看系统指令手册", "系统有什么功能"),
                        List.of("What can this system do?", "Show the command manual", "How do I use the system?", "Show available capabilities")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, EmptyInput input) {
        SelectionCardData shortcuts = SelectionCardData.shortcuts("system.result.help", "system.tts.message.help", context.shortcuts());
        return AgentResult.data("system.result.help", new SelectionCardData(
                Component.translatable("system.result.help").append("\n").append(String.join("\n", context.commandUsages())),
                shortcuts.speech(), shortcuts.choices()));
    }
}
