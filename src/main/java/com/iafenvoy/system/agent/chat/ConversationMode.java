package com.iafenvoy.system.agent.chat;

import com.iafenvoy.system.agent.routing.RouteDecision;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * One player preference controls routing and conversation; administrator permission remains independent.
 */
public enum ConversationMode implements StringRepresentable, ConversationStrategy {
    OFFLINE {
        @Override
        public RouteDecision route(Routes routes) {
            return routes.local();
        }
    },
    STANDARD {
        @Override
        public RouteDecision route(Routes routes) throws Exception {
            RouteDecision local = routes.local();
            return local.input() == null ? routes.assisted() : local;
        }
    },
    EXTENDED {
        @Override
        public RouteDecision route(Routes routes) throws Exception {
            RouteDecision local = routes.local();
            if (local.input() != null) return local;
            RouteDecision assisted = routes.assisted();
            return assisted.input() == null && assisted.source() == RouteSource.LLM ? routes.chat() : assisted;
        }
    },
    FREE {
        @Override
        public RouteDecision route(Routes routes) {
            return routes.chat();
        }
    };

    public static final Codec<ConversationMode> CODEC = StringRepresentable.fromEnum(ConversationMode::values);
    public static final StreamCodec<ByteBuf, ConversationMode> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    @Override
    public boolean usesAi() {
        return this != OFFLINE;
    }

    @Override
    public boolean chats() {
        return this == EXTENDED || this == FREE;
    }

    public String labelKey() {
        return "system.chat_mode." + this.getSerializedName();
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
