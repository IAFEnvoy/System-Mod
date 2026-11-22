package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.assistant.action.AgentAction;
import com.mojang.serialization.MapCodec;

public record SpaceDepositInput(String reference, int amount, boolean stack) implements AgentAction {
    public static final SchemaMapCodec<SpaceDepositInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("reference", SchemaCodecs.string(0, 36).described("Server-issued inventory reference; empty selects the current main hand."), "").forGetter(SpaceDepositInput::reference),
            f.optional("amount", SchemaCodecs.intRange(1, 2304), 1).forGetter(SpaceDepositInput::amount),
            f.optional("stack", SchemaCodecs.BOOL.described("Deposit up to one native stack from the selected slot."), false).forGetter(SpaceDepositInput::stack)
    ).apply(i, SpaceDepositInput::new));

    public static SpaceDepositInput command(String text) {
        String value = text.strip();
        if (value.isEmpty()) return new SpaceDepositInput("", 1, false);
        if (value.equalsIgnoreCase("stack") || value.equals("一组")) return new SpaceDepositInput("", 1, true);
        SpaceTakeInput held = SpaceTakeInput.parse("取出" + value);
        if (held != null && held.query().matches("手持物品|手中物品|手里的物品"))
            return new SpaceDepositInput("", held.amount(), held.stack());
        try {
            int amount = Integer.parseInt(value);
            if (amount < 1 || amount > 2304) throw new NumberFormatException();
            return new SpaceDepositInput("", amount, false);
        } catch (NumberFormatException ignored) {
            throw new AgentException(AgentFailure.INVALID_INPUT);
        }
    }

    public static SpaceDepositInput parse(String text) {
        String value = text.strip();
        if (value.matches("(?i)^deposit(?:\\s+.*)?$")) {
            try {
                return command(value.substring(7));
            } catch (AgentException ignored) {
                return null;
            }
        }
        if (!value.startsWith("存入") && !value.startsWith("存放")) return null;
        value = value.replaceFirst("(?:到|至|进)(?:系统空间|系统背包)[。！!]?$", "");
        SpaceTakeInput parsed = SpaceTakeInput.parse("取出" + value.substring(2));
        if (parsed == null || !parsed.query().matches("手持物品|手中物品|手里的物品")) return null;
        return new SpaceDepositInput("", parsed.amount(), parsed.stack());
    }

    @Override
    public MapCodec<SpaceDepositInput> codec() {
        return CODEC;
    }
}
