package com.iafenvoy.system.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Client-facing snapshot of the binding, display preferences and advertised modules.
 */
public record ClientState(Optional<Identifier> system, boolean historySaved, boolean externalModel,
                          List<Identifier> modules, List<String> commands, long requestTimeoutMillis) {
    public static final MapCodec<ClientState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("system").forGetter(ClientState::system),
            Codec.BOOL.fieldOf("historySaved").forGetter(ClientState::historySaved),
            Codec.BOOL.fieldOf("externalModel").forGetter(ClientState::externalModel),
            Identifier.CODEC.listOf().fieldOf("modules").forGetter(ClientState::modules),
            Codec.STRING.listOf().fieldOf("commands").forGetter(ClientState::commands),
            Codec.LONG.fieldOf("requestTimeoutMillis").forGetter(ClientState::requestTimeoutMillis)
    ).apply(i, ClientState::new));

    public ClientState {
        modules = List.copyOf(modules);
        commands = List.copyOf(commands);
    }
}
