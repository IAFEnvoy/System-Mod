package com.iafenvoy.system.module.builtin.anchor;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.AnchorTeleportInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorTeleportModule extends AbstractAgentModule<AnchorTeleportInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:(?:传送到锚点|传送锚点|前往锚点)\\s*(.+)|(?:teleport to anchor|go to anchor)\\s+(.+))$", Pattern.CASE_INSENSITIVE);

    public AnchorTeleportModule(ToolRegistry.Tool<AnchorTeleportInput, AnchorListOutput> tool) {
        super(AnchorTeleportInput.CODEC, "Teleport me to an existing personal anchor by observed ID or exact name. Never invent anchor IDs or coordinates. Subject to capacity, cooldown and destination safety checks.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<AnchorTeleportInput> routing() {
        return ModuleRouting.<AnchorTeleportInput>defaults()
                .button(a -> new AnchorTeleportInput(a.required("anchor"), true))
                .command(AgentCommand.argument("<anchor>", value -> new AnchorTeleportInput(value, false), "anchor_tp", "传送锚点"))
                .explicit(AnchorTeleportModule::parse).explicitOnly()
                .semantic(RoutingProfile.hints(List.of("传送到锚点家"), List.of("Teleport to anchor home")));
    }

    private static AnchorTeleportInput parse(String text) {
        Matcher matcher = EXPLICIT.matcher(text.strip());
        if (!matcher.matches()) return null;
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return new AnchorTeleportInput(value == null ? "" : value.strip(), false);
    }

    @Override
    public AgentResult execute(ExecutionContext context, AnchorTeleportInput input) throws Exception {
        AgentResult result = AgentResult.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_TELEPORT, "teleport", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
