package com.iafenvoy.system.data.usage;

import com.iafenvoy.system.config.file.SpeechPricingConfig;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.OptionalLong;

import static com.iafenvoy.system.data.usage.TokenUsage.add;

/**
 * Recognition tracks calls and API-reported audio duration, separately from TTS and model tokens.
 */
public record RecognitionUsage(long calls, long failedCalls, long unreportedCalls, long estimatedNanoYuan,
                               long unpricedCalls, long durationMillis) {
    public static final RecognitionUsage EMPTY = new RecognitionUsage(0, 0, 0, 0, 0, 0);
    public static final Codec<RecognitionUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("calls").forGetter(RecognitionUsage::calls),
            Codec.LONG.fieldOf("failed_calls").forGetter(RecognitionUsage::failedCalls),
            Codec.LONG.fieldOf("unreported_calls").forGetter(RecognitionUsage::unreportedCalls),
            Codec.LONG.fieldOf("estimated_nano_yuan").forGetter(RecognitionUsage::estimatedNanoYuan),
            Codec.LONG.fieldOf("unpriced_calls").forGetter(RecognitionUsage::unpricedCalls),
            Codec.LONG.lenientOptionalFieldOf("duration_ms", 0L).forGetter(RecognitionUsage::durationMillis)
    ).apply(i, RecognitionUsage::new));

    public RecognitionUsage {
        if (failedCalls < 0 || failedCalls > calls || unreportedCalls < 0 || unreportedCalls > calls
                || estimatedNanoYuan < 0 || unpricedCalls < 0 || unpricedCalls > calls || durationMillis < 0)
            throw new IllegalArgumentException("Invalid recognition usage totals");
    }

    public RecognitionUsage begin() {
        return new RecognitionUsage(add(this.calls, 1), this.failedCalls, add(this.unreportedCalls, 1),
                this.estimatedNanoYuan, add(this.unpricedCalls, 1), this.durationMillis);
    }

    public RecognitionUsage complete(String provider, String region, AsrProvider.Usage usage, boolean successful) {
        OptionalLong price = SpeechPricingConfig.estimateRecognition(provider, region, usage, successful);
        return new RecognitionUsage(this.calls, add(this.failedCalls, successful ? 0 : 1), this.unreportedCalls - 1,
                add(this.estimatedNanoYuan, price.orElse(0)), this.unpricedCalls - (price.isPresent() ? 1 : 0),
                add(this.durationMillis, usage.durationMillis()));
    }

    public RecognitionUsage merge(RecognitionUsage other) {
        return new RecognitionUsage(add(this.calls, other.calls), add(this.failedCalls, other.failedCalls),
                add(this.unreportedCalls, other.unreportedCalls), add(this.estimatedNanoYuan, other.estimatedNanoYuan),
                add(this.unpricedCalls, other.unpricedCalls), add(this.durationMillis, other.durationMillis));
    }
}
