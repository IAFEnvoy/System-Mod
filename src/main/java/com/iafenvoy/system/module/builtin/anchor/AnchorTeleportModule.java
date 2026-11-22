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
import com.iafenvoy.system.data.input.AnchorTeleportInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorTeleportModule extends AbstractAgentModule<AnchorTeleportInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:(?:传送到锚点|传送锚点|前往锚点)\\s*(.+)|(?:teleport to anchor|go to anchor)\\s+(.+))$", Pattern.CASE_INSENSITIVE);

    public AnchorTeleportModule(ToolRegistry.Tool<AnchorTeleportInput, AnchorListOutput, ExecutionContext> tool) {
        super(AnchorTeleportInput.CODEC, "Travel to an observed personal anchor ID/exact name or a public:<namespace>:<path> destination. Paid travel only previews a fee; the player must confirm. Never invent IDs or coordinates. Subject to capacity, cooldown and destination safety checks.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<AnchorTeleportInput> routing() {
        return ModuleRouting.<AnchorTeleportInput>defaults()
                .button(a -> new AnchorTeleportInput(a.required("anchor"), true, a.optional("quote")))
                .command(AgentCommand.argument("<anchor>", value -> new AnchorTeleportInput(value, false), "anchor_tp", "传送锚点"))
                .command(AgentCommand.identifier("destination", id -> new AnchorTeleportInput("public:" + id, false), "destination", "公共传送"))
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
    public AgentResult<CardUpdate> execute(ExecutionContext context, AnchorTeleportInput input) throws Exception {
        AgentResult<CardUpdate> result = SystemResults.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_TELEPORT, "teleport", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
