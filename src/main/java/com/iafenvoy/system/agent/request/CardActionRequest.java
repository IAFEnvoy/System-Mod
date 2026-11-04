package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardReference;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The authenticated connection supplies identity; source is presentation context only.
 */
public record CardActionRequest(CardReference source, CardAction.Server action) implements AgentRequest {
    public static final MapCodec<CardActionRequest> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CardReference.CODEC.fieldOf("source").forGetter(CardActionRequest::source),
            CardAction.Server.CODEC.fieldOf("action").forGetter(CardActionRequest::action)
    ).apply(i, CardActionRequest::new));

    @Override
    public MapCodec<CardActionRequest> codec() {
        return CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
