package com.iafenvoy.system.data.usage;

import com.iafenvoy.system.agent.model.ModelClient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record TokenUsage(long inputTokens, long outputTokens, long totalTokens, long cachedInputTokens) {
    public static final TokenUsage EMPTY = new TokenUsage(0, 0, 0, 0);
    // Keep saved fields flat; the network codec transmits the same immutable value directly.
    public static final MapCodec<TokenUsage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.fieldOf("input_tokens").forGetter(TokenUsage::inputTokens),
            Codec.LONG.fieldOf("output_tokens").forGetter(TokenUsage::outputTokens),
            Codec.LONG.fieldOf("total_tokens").forGetter(TokenUsage::totalTokens),
            Codec.LONG.fieldOf("cached_input_tokens").forGetter(TokenUsage::cachedInputTokens)
    ).apply(i, TokenUsage::new));
    public static final StreamCodec<ByteBuf, TokenUsage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TokenUsage::inputTokens,
            ByteBufCodecs.VAR_LONG, TokenUsage::outputTokens,
            ByteBufCodecs.VAR_LONG, TokenUsage::totalTokens,
            ByteBufCodecs.VAR_LONG, TokenUsage::cachedInputTokens, TokenUsage::new);

    public TokenUsage {
        if (outputTokens < 0 || totalTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens)
            throw new IllegalArgumentException("Invalid saved token counts");
    }

    public TokenUsage(ModelClient.Usage usage) {
        this(usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), usage.cachedInputTokens());
    }

    public TokenUsage merge(TokenUsage other) {
        return new TokenUsage(
                add(this.inputTokens, other.inputTokens),
                add(this.outputTokens, other.outputTokens),
                add(this.totalTokens, other.totalTokens),
                add(this.cachedInputTokens, other.cachedInputTokens));
    }

    public static long add(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
