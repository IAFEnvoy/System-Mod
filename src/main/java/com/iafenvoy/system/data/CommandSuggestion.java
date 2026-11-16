package com.iafenvoy.system.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A completed assistant input and its optional tooltip; no game command nodes are synchronized.
 */
public record CommandSuggestion(String value, String hint) {
    public static final StreamCodec<ByteBuf, CommandSuggestion> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(4096), CommandSuggestion::value,
            ByteBufCodecs.stringUtf8(512), CommandSuggestion::hint, CommandSuggestion::new);
}
