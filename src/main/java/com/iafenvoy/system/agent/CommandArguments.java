package com.iafenvoy.system.agent;

import net.minecraft.resources.Identifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared syntax checks for trusted command and local semantic parsers.
 */
public final class CommandArguments {
    private static final Pattern ITEM_ID = Pattern.compile("(?<![a-z0-9_.:/-])([a-z0-9_.-]+:[a-z0-9_./-]+)(?![a-z0-9_.:/-])");

    private CommandArguments() {
    }

    public static Identifier identifier(String text) {
        Identifier id = Identifier.tryParse(text);
        if (id == null) throw new AgentException("INVALID_INPUT");
        return id;
    }

    public static String singleItemId(String text) {
        Matcher matcher = ITEM_ID.matcher(text);
        if (!matcher.find()) return null;
        String id = matcher.group(1);
        return matcher.find() ? null : id;
    }
}
