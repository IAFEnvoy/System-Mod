package com.iafenvoy.system.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Client-facing snapshot of the binding, display preferences and advertised modules.
 */
public record ClientState(Optional<ResourceLocation> system, boolean historySaved,
                          List<ResourceLocation> modules, List<String> commands, long requestTimeoutMillis, AiStatus ai) {
    public static final StreamCodec<ByteBuf, ClientState> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional), ClientState::system,
            ByteBufCodecs.BOOL, ClientState::historySaved,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ClientState::modules,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(256)), ClientState::commands,
            ByteBufCodecs.VAR_LONG, ClientState::requestTimeoutMillis,
            AiStatus.STREAM_CODEC, ClientState::ai, ClientState::new);

    public ClientState {
        modules = List.copyOf(modules);
        commands = List.copyOf(commands);
    }
}
