package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SpaceTakeInput(String entry, String query, int amount, boolean stack,
                             boolean inPlace) implements AgentAction {
    public static final SchemaMapCodec<SpaceTakeInput> CODEC = SchemaCodecs.<SpaceTakeInput>record((i, f) -> i.group(
            f.optional("entry", SchemaCodecs.STRING.described("Observed system-space entry ID, never invent one."), "").forGetter(SpaceTakeInput::entry),
            f.optional("query", SchemaCodecs.STRING.described("Stored content name or item ID; use only when entry is empty."), "").forGetter(SpaceTakeInput::query),
            f.optional("amount", SchemaCodecs.intRange(1, 2304), 1).forGetter(SpaceTakeInput::amount),
            f.optional("stack", SchemaCodecs.BOOL.described("Take one native stack/batch instead of amount; capped by stored quantity."), false).forGetter(SpaceTakeInput::stack),
            f.optional("in_place", SchemaCodecs.BOOL.described("Replace the originating space card; leave false for text or tool requests."), false).forGetter(SpaceTakeInput::inPlace)
    ).apply(i, SpaceTakeInput::new)).validate(input -> input.entry.isBlank() == input.query.isBlank()
            ? DataResult.error(() -> "Exactly one space selector is required") : DataResult.success(input));
    private static final Pattern CHINESE = Pattern.compile("^(?:从(?:系统空间|系统背包|收件箱)(?:里|中)?(?:面)?)?(?:取出|拿出|提取)\\s*(?:(一组|1组)|([0-9]+|[零〇一二两三四五六七八九十百]+(?=\\s*(?:个|块|件|枚|颗|本|把|份)))\\s*(?:个|块|件|枚|颗|本|把|份)?)?\\s*(.+?)[。.!！]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH = Pattern.compile("^(?:take|withdraw)\\s+(?:(a stack of|one stack of)|([0-9]+))?\\s*(.+?)(?:\\s+from (?:my |the )?(?:system )?(?:space|inbox))?[.!]?$", Pattern.CASE_INSENSITIVE);

    public static SpaceTakeInput parse(String text) {
        String value = text.strip().replaceFirst("^/", "");
        Matcher match = CHINESE.matcher(value);
        if (!match.matches()) {
            match = ENGLISH.matcher(value);
            if (!match.matches()) return null;
        }
        try {
            int amount = match.group(2) == null ? 1 : number(match.group(2));
            if (amount < 1 || amount > 2304) return null;
            return new SpaceTakeInput("", match.group(3).strip(), amount, match.group(1) != null, false);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int number(String value) {
        if (value.matches("[0-9]+")) return Integer.parseInt(value);
        int total = 0, digit = 0;
        for (char c : value.toCharArray()) {
            if (c == '十' || c == '百') {
                total += (digit == 0 ? 1 : digit) * (c == '十' ? 10 : 100);
                digit = 0;
            } else {
                digit = "零一二三四五六七八九".indexOf(c == '两' ? '二' : c == '〇' ? '零' : c);
                if (digit < 0) throw new IllegalArgumentException("Invalid quantity");
            }
        }
        return total + digit;
    }

    @Override
    public MapCodec<SpaceTakeInput> codec() {
        return CODEC;
    }
}
