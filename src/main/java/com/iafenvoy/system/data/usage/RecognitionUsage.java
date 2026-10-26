package com.iafenvoy.system.data.usage;

import com.iafenvoy.system.config.AliyunSpeechPricing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.OptionalLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import static com.iafenvoy.system.data.usage.TokenUsage.add;

/** Recognition is priced per successful call, separately from TTS characters and model tokens. */
public record RecognitionUsage(long calls, long failedCalls, long unreportedCalls, long estimatedNanoYuan, long unpricedCalls) {
    public static final RecognitionUsage EMPTY = new RecognitionUsage(0, 0, 0, 0, 0);
    public static final Codec<RecognitionUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("calls").forGetter(RecognitionUsage::calls),
            Codec.LONG.fieldOf("failed_calls").forGetter(RecognitionUsage::failedCalls),
            Codec.LONG.fieldOf("unreported_calls").forGetter(RecognitionUsage::unreportedCalls),
            Codec.LONG.fieldOf("estimated_nano_yuan").forGetter(RecognitionUsage::estimatedNanoYuan),
            Codec.LONG.fieldOf("unpriced_calls").forGetter(RecognitionUsage::unpricedCalls)
    ).apply(i, RecognitionUsage::new));
    public static final StreamCodec<ByteBuf, RecognitionUsage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RecognitionUsage::calls,
            ByteBufCodecs.VAR_LONG, RecognitionUsage::failedCalls,
            ByteBufCodecs.VAR_LONG, RecognitionUsage::unreportedCalls,
            ByteBufCodecs.VAR_LONG, RecognitionUsage::estimatedNanoYuan,
            ByteBufCodecs.VAR_LONG, RecognitionUsage::unpricedCalls, RecognitionUsage::new);

    public RecognitionUsage {
        if (failedCalls < 0 || failedCalls > calls || unreportedCalls < 0 || unreportedCalls > calls
                || estimatedNanoYuan < 0 || unpricedCalls < 0 || unpricedCalls > calls)
            throw new IllegalArgumentException("Invalid recognition usage totals");
    }

    public RecognitionUsage begin() {
        return new RecognitionUsage(add(this.calls, 1), this.failedCalls, add(this.unreportedCalls, 1),
                this.estimatedNanoYuan, add(this.unpricedCalls, 1));
    }

    public RecognitionUsage complete(String provider, String region, boolean successful) {
        OptionalLong price = successful ? AliyunSpeechPricing.estimateRecognition(provider, region) : OptionalLong.empty();
        return new RecognitionUsage(this.calls, add(this.failedCalls, successful ? 0 : 1), this.unreportedCalls - 1,
                add(this.estimatedNanoYuan, price.orElse(0)), this.unpricedCalls - (price.isPresent() ? 1 : 0));
    }

    public RecognitionUsage merge(RecognitionUsage other) {
        return new RecognitionUsage(add(this.calls, other.calls), add(this.failedCalls, other.failedCalls),
                add(this.unreportedCalls, other.unreportedCalls), add(this.estimatedNanoYuan, other.estimatedNanoYuan),
                add(this.unpricedCalls, other.unpricedCalls));
    }
}
