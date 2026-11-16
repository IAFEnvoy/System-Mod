package com.iafenvoy.system.module.builtin.anchor;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.AnchorSaveInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorSaveModule extends AbstractAgentModule<AnchorSaveInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:(?:设置|创建|保存)锚点\\s*(.*)|save anchor(?:\\s+(.*))?)$", Pattern.CASE_INSENSITIVE);

    public AnchorSaveModule(ToolRegistry.Tool<AnchorSaveInput, AnchorListOutput> tool) {
        super(AnchorSaveInput.CODEC, "Save my CURRENT position as a personal teleport anchor. Never accept remote coordinates. Empty name generates a name. Saving is limited by dynamically unlocked capacity.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<AnchorSaveInput> routing() {
        return ModuleRouting.<AnchorSaveInput>defaults()
                .button(a -> new AnchorSaveInput(a.text("name"), true))
                .command(AgentCommand.argument("[name]", value -> new AnchorSaveInput(value, false), "anchor_save", "设置锚点", "保存锚点"))
                .explicit(AnchorSaveModule::parse).explicitOnly()
                .semantic(RoutingProfile.hints(List.of("保存当前位置为锚点家"), List.of("Save anchor home")));
    }

    private static AnchorSaveInput parse(String text) {
        Matcher matcher = EXPLICIT.matcher(text.strip());
        if (!matcher.matches()) return null;
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return new AnchorSaveInput(value == null ? "" : value.strip(), false);
    }

    @Override
    public AgentResult execute(ExecutionContext context, AnchorSaveInput input) throws Exception {
        AgentResult result = AgentResult.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_SAVE, "save", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
