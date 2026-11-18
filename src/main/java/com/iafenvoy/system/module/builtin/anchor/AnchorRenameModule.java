package com.iafenvoy.system.module.builtin.anchor;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.command.AssistantCommandSource;
import com.iafenvoy.system.data.input.AnchorRenameInput;
import com.iafenvoy.system.data.output.AnchorListOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorRenameModule extends AbstractAgentModule<AnchorRenameInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:重命名锚点\\s*(.+?)\\s+(?:为|成|->|→)\\s*(.+)|rename anchor\\s+(.+?)\\s+to\\s+(.+))$", Pattern.CASE_INSENSITIVE);

    public AnchorRenameModule(ToolRegistry.Tool<AnchorRenameInput, AnchorListOutput> tool) {
        super(AnchorRenameInput.CODEC, "Rename an existing personal teleport anchor by observed ID or exact current name, only when explicitly requested. Preserve its destination and slot; never delete or recreate it.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<AnchorRenameInput> routing() {
        return ModuleRouting.<AnchorRenameInput>defaults()
                .button(a -> new AnchorRenameInput(a.required("anchor"), a.required("name"), true))
                .command(new AgentCommand(List.of("anchor_rename", "重命名锚点"), name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name)
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("anchor", AnchorRenameModule::selector)
                                .then(RequiredArgumentBuilder.<AssistantCommandSource, String>argument("name", StringArgumentType.greedyString())
                                        .executes(AnchorRenameModule::command)))))
                .explicit(AnchorRenameModule::parse).explicitOnly()
                .semantic(RoutingProfile.hints(List.of("重命名锚点 家 为 基地"), List.of("Rename anchor home to base")));
    }

    private static int command(CommandContext<AssistantCommandSource> command) throws CommandSyntaxException {
        // Bare Chinese prose may match the command alias before the explicit-intent parser runs.
        AnchorRenameInput input = parse(command.getInput());
        if (input == null) input = new AnchorRenameInput(StringArgumentType.getString(command, "anchor"),
                newName(StringArgumentType.getString(command, "name")), false);
        return command.getSource().action(input);
    }

    private static String selector(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '"') return reader.readQuotedString();
        int start = reader.getCursor();
        // Bare selectors also accept Chinese names; quoted selectors retain Brigadier escaping for spaces.
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    private static String newName(String value) throws CommandSyntaxException {
        StringReader reader = new StringReader(value);
        if (!reader.canRead() || reader.peek() != '"') return value.strip();
        String name = reader.readQuotedString();
        reader.skipWhitespace();
        if (reader.canRead())
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
        return name;
    }

    private static AnchorRenameInput parse(String text) {
        Matcher matcher = EXPLICIT.matcher(text.strip());
        if (!matcher.matches()) return null;
        try {
            return new AnchorRenameInput(newName((matcher.group(1) != null ? matcher.group(1) : matcher.group(3)).strip()),
                    newName((matcher.group(2) != null ? matcher.group(2) : matcher.group(4)).strip()), false);
        } catch (CommandSyntaxException ignored) {
            return null;
        }
    }

    @Override
    public AgentResult execute(ExecutionContext context, AnchorRenameInput input) throws Exception {
        AgentResult result = AgentResult.data("system.result.anchor_list", context.tool(BuiltinTools.ANCHOR_RENAME, "rename", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), input.inPlace());
    }
}
