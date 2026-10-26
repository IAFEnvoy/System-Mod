package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Only lifecycle metadata reaches the server; audio, recognized text and NLS credentials never do.
 */
public record RecognitionUsageReportC2SPayload(UUID id, boolean complete, String provider, String region,
                                               boolean successful) implements CustomPacketPayload {
    public static final Type<RecognitionUsageReportC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "recognition_usage_report_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecognitionUsageReportC2SPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RecognitionUsageReportC2SPayload::id,
            ByteBufCodecs.BOOL, RecognitionUsageReportC2SPayload::complete,
            ByteBufCodecs.stringUtf8(32), RecognitionUsageReportC2SPayload::provider,
            ByteBufCodecs.stringUtf8(32), RecognitionUsageReportC2SPayload::region,
            ByteBufCodecs.BOOL, RecognitionUsageReportC2SPayload::successful, RecognitionUsageReportC2SPayload::new);

    public RecognitionUsageReportC2SPayload {
        if (!provider.matches("[a-z0-9_.:-]{1,32}") || !region.matches("[a-z0-9_.-]{1,32}") || !complete && successful)
            throw new IllegalArgumentException("Invalid recognition usage report");
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
