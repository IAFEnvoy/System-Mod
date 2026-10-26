package com.iafenvoy.system.data.usage;

import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.config.AliyunSpeechPricing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import static com.iafenvoy.system.data.usage.TokenUsage.add;

/** Immutable client-reported TTS totals, stored beside server-observed AI tokens in the world ledger. */
public record SpeechUsage(long characters, TokenUsage tokens, long calls, long failedCalls,
                          long unreportedCalls, long estimatedNanoYuan, long unpricedCalls) {
    public static final SpeechUsage EMPTY = new SpeechUsage(0, TokenUsage.EMPTY, 0, 0, 0, 0, 0);
    public static final Codec<SpeechUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("characters").forGetter(SpeechUsage::characters),
            TokenUsage.CODEC.codec().fieldOf("tokens").forGetter(SpeechUsage::tokens),
            Codec.LONG.fieldOf("calls").forGetter(SpeechUsage::calls),
            Codec.LONG.fieldOf("failed_calls").forGetter(SpeechUsage::failedCalls),
            Codec.LONG.fieldOf("unreported_calls").forGetter(SpeechUsage::unreportedCalls),
            Codec.LONG.fieldOf("estimated_nano_yuan").forGetter(SpeechUsage::estimatedNanoYuan),
            Codec.LONG.fieldOf("unpriced_calls").forGetter(SpeechUsage::unpricedCalls)
    ).apply(i, SpeechUsage::new));
    public static final StreamCodec<ByteBuf, SpeechUsage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SpeechUsage::characters,
            TokenUsage.STREAM_CODEC, SpeechUsage::tokens,
            ByteBufCodecs.VAR_LONG, SpeechUsage::calls,
            ByteBufCodecs.VAR_LONG, SpeechUsage::failedCalls,
            ByteBufCodecs.VAR_LONG, SpeechUsage::unreportedCalls,
            ByteBufCodecs.VAR_LONG, SpeechUsage::estimatedNanoYuan,
            ByteBufCodecs.VAR_LONG, SpeechUsage::unpricedCalls, SpeechUsage::new);

    public SpeechUsage {
        Objects.requireNonNull(tokens);
        if (characters < 0 || failedCalls < 0 || failedCalls > calls || unreportedCalls < 0
                || unreportedCalls > calls || estimatedNanoYuan < 0 || unpricedCalls < 0 || unpricedCalls > calls)
            throw new IllegalArgumentException("Invalid speech usage totals");
    }

    public SpeechUsage begin() {
        return new SpeechUsage(this.characters, this.tokens, add(this.calls, 1), this.failedCalls,
                add(this.unreportedCalls, 1), this.estimatedNanoYuan, add(this.unpricedCalls, 1));
    }

    public SpeechUsage complete(String provider, String model, String region, TtsProvider.Usage usage, boolean successful) {
        OptionalLong price = AliyunSpeechPricing.estimate(provider, model, region, usage);
        TokenUsage tokens = new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), 0);
        return new SpeechUsage(add(this.characters, usage.characters()), this.tokens.merge(tokens), this.calls,
                add(this.failedCalls, successful ? 0 : 1), this.unreportedCalls - (usage.reported() ? 1 : 0),
                add(this.estimatedNanoYuan, price.orElse(0)), this.unpricedCalls - (price.isPresent() ? 1 : 0));
    }

    public SpeechUsage merge(SpeechUsage other) {
        return new SpeechUsage(add(this.characters, other.characters), this.tokens.merge(other.tokens),
                add(this.calls, other.calls), add(this.failedCalls, other.failedCalls), add(this.unreportedCalls, other.unreportedCalls),
                add(this.estimatedNanoYuan, other.estimatedNanoYuan), add(this.unpricedCalls, other.unpricedCalls));
    }
}
