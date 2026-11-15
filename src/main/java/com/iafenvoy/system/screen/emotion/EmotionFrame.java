package com.iafenvoy.system.screen.emotion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** A texture with a draw-only offset in document pixels; a bare Identifier means zero offset. */
public record EmotionFrame(Identifier texture, Offset offset) {
    public static final Codec<EmotionFrame> CODEC = Codec.withAlternative(RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("texture").forGetter(EmotionFrame::texture),
            Offset.CODEC.lenientOptionalFieldOf("offset", Offset.ZERO).forGetter(EmotionFrame::offset)
    ).apply(i, EmotionFrame::new)), Identifier.CODEC.xmap(EmotionFrame::new, EmotionFrame::texture));

    public EmotionFrame(Identifier texture) {
        this(texture, Offset.ZERO);
    }

    public record Offset(float x, float y) {
        public static final Offset ZERO = new Offset(0, 0);
        public static final Codec<Offset> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.lenientOptionalFieldOf("x", 0F).forGetter(Offset::x),
                Codec.FLOAT.lenientOptionalFieldOf("y", 0F).forGetter(Offset::y)
        ).apply(i, Offset::new));
    }
}
