package com.iafenvoy.system.module.builtin.anchor;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.AnchorDeleteInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorDeleteModule extends AbstractAgentModule<AnchorDeleteInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:删除锚点\\s*(.+)|delete anchor\\s+(.+))$", Pattern.CASE_INSENSITIVE);

    public AnchorDeleteModule(ToolRegistry.Tool<AnchorDeleteInput, AnchorListOutput, ExecutionContext> tool) {
        super(AnchorDeleteInput.CODEC, "Permanently delete a personal teleport anchor by observed ID or exact name. Does not teleport. Only do this when explicitly requested.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<AnchorDeleteInput> routing() {
        return ModuleRouting.<AnchorDeleteInput>defaults()
                .button(a -> new AnchorDeleteInput(a.required("anchor"), true))
                .command(AgentCommand.argument("<anchor>", value -> new AnchorDeleteInput(value, false), "anchor_delete", "删除锚点"))
                .explicit(AnchorDeleteModule::parse).explicitOnly()
                .semantic(RoutingProfile.hints(List.of("删除锚点家"), List.of("Delete anchor home")));
    }

    private static AnchorDeleteInput parse(String text) {
        Matcher matcher = EXPLICIT.matcher(text.strip());
        if (!matcher.matches()) return null;
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return new AnchorDeleteInput(value == null ? "" : value.strip(), false);
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, AnchorDeleteInput input) throws Exception {
        AgentResult<CardUpdate> result = SystemResults.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_DELETE, "delete", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
