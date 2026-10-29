package com.iafenvoy.system.data;

import com.iafenvoy.system.module.ServerModule;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** A datapack entry pairs configured server behavior with an optional client implementation ID. */
public record SystemModule(Optional<ServerModule> server, Optional<Identifier> client) {
    public static final Codec<SystemModule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ServerModule.CODEC.optionalFieldOf("server").forGetter(SystemModule::server),
            Identifier.CODEC.optionalFieldOf("client").forGetter(SystemModule::client)
    ).apply(i, SystemModule::new));

    public boolean available() {
        return this.server.map(ServerModule::available).orElse(true);
    }
}
