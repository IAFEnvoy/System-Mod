package com.iafenvoy.system.screen.card.node.text;

import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared fields and formatting, without inheriting another element's presentation capabilities.
 */
public record TextContent(String text, String translate, List<String> args, boolean inline) {
    public static final MapCodec<TextContent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("text", "").forGetter(TextContent::text),
            Codec.STRING.lenientOptionalFieldOf("translate", "").forGetter(TextContent::translate),
            Codec.STRING.listOf().lenientOptionalFieldOf("args", List.of()).forGetter(TextContent::args),
            Codec.BOOL.lenientOptionalFieldOf("inline", false).forGetter(TextContent::inline)
    ).apply(i, TextContent::new));

    public TextContent {
        args = List.copyOf(args);
    }

    public static TextContent of(String text) {
        return new TextContent(text, "", List.of(), false);
    }

    public String resolve(CardContext context, List<Integer> indices) {
        if (this.translate.isEmpty()) return CardRenderer.value(this.text, context, indices);
        return LegacyText.scoped(Component.translatable(this.translate, this.args.stream()
                .map(arg -> CardRenderer.value(arg, context, indices)).toArray()).getString());
    }
}
