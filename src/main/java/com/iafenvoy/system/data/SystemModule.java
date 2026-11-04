package com.iafenvoy.system.data;

import com.iafenvoy.system.module.ServerModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A datapack entry pairs configured behavior with a client implementation ID and display order.
 */
public record SystemModule(Optional<ServerModule> server, Optional<Identifier> client, int order) {
    public static final Codec<SystemModule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ServerModule.CODEC.optionalFieldOf("server").forGetter(SystemModule::server),
            Identifier.CODEC.optionalFieldOf("client").forGetter(SystemModule::client),
            Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(SystemModule::order)
    ).apply(i, SystemModule::new));

    public boolean available() {
        return this.server.map(ServerModule::available).orElse(true);
    }
}
