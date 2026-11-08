package com.iafenvoy.system.data.module;

import com.iafenvoy.system.module.ServerModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A datapack entry pairs configured behavior with a client implementation ID and display order.
 */
public record SystemModule(Optional<ServerModule> server, Optional<ResourceLocation> client, int order) {
    public static final Codec<SystemModule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ServerModule.CODEC.optionalFieldOf("server").forGetter(SystemModule::server),
            ResourceLocation.CODEC.optionalFieldOf("client").forGetter(SystemModule::client),
            Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(SystemModule::order)
    ).apply(i, SystemModule::new));

    public boolean available() {
        return this.server.map(ServerModule::available).orElse(true);
    }
}
