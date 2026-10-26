package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.speech.tts.TtsProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Client accounting only: no credentials, spoken text, voice ID or client-selected player identity.
 */
public record SpeechUsageReportC2SPayload(UUID id, boolean complete, String provider, String model, String region,
                                          TtsProvider.Usage usage, boolean successful) implements CustomPacketPayload {
    public static final Type<SpeechUsageReportC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "speech_usage_report_c2s"));
    private static final StreamCodec<ByteBuf, TtsProvider.Unit> UNIT_CODEC = ByteBufCodecs.stringUtf8(16).map(TtsProvider.Unit::valueOf, Enum::name);
    private static final StreamCodec<ByteBuf, TtsProvider.Usage> USAGE_CODEC = StreamCodec.composite(
            UNIT_CODEC, TtsProvider.Usage::unit,
            ByteBufCodecs.BOOL, TtsProvider.Usage::reported,
            ByteBufCodecs.VAR_LONG, TtsProvider.Usage::characters,
            ByteBufCodecs.VAR_LONG, TtsProvider.Usage::inputTokens,
            ByteBufCodecs.VAR_LONG, TtsProvider.Usage::outputTokens,
            ByteBufCodecs.VAR_LONG, TtsProvider.Usage::totalTokens, TtsProvider.Usage::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeechUsageReportC2SPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SpeechUsageReportC2SPayload::id,
            ByteBufCodecs.BOOL, SpeechUsageReportC2SPayload::complete,
            ByteBufCodecs.stringUtf8(32), SpeechUsageReportC2SPayload::provider,
            ByteBufCodecs.stringUtf8(80), SpeechUsageReportC2SPayload::model,
            ByteBufCodecs.stringUtf8(32), SpeechUsageReportC2SPayload::region,
            USAGE_CODEC, SpeechUsageReportC2SPayload::usage,
            ByteBufCodecs.BOOL, SpeechUsageReportC2SPayload::successful, SpeechUsageReportC2SPayload::new);

    public SpeechUsageReportC2SPayload {
        if (provider.length() > 32 || model.length() > 80 || region.length() > 32 || usage.characters() > 2400
                || usage.inputTokens() > 1_000_000 || usage.outputTokens() > 1_000_000 || usage.totalTokens() > 1_000_000
                || !complete && (!usage.equals(TtsProvider.Usage.UNKNOWN) || successful))
            throw new IllegalArgumentException("Invalid speech usage report");
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
