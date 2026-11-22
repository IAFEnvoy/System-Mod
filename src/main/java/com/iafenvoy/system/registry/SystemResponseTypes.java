package com.iafenvoy.system.registry;

import com.iafenvoy.assistant.registry.AssistantResourceKeys;
import com.iafenvoy.assistant.response.AgentResponse;
import com.iafenvoy.assistant.response.ResultResponses;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.response.*;
import com.iafenvoy.system.data.card.CardUpdate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemResponseTypes {
    public static final ResultResponses<CardUpdate> RESULTS = new ResultResponses<>(SystemResults.CODEC);
    public static final DeferredRegister<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>> REGISTRY = DeferredRegister.create(AssistantResourceKeys.AGENT_RESPONSE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<ByteBuf, StateResponse>> STATE = REGISTRY.register("state", () -> StateResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, HistoryResponse>> HISTORY = REGISTRY.register("history", () -> HistoryResponse.STREAM_CODEC);
    static {
        REGISTRY.register("reply", RESULTS::replyCodec);
        REGISTRY.register("notice", RESULTS::noticeCodec);
    }
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<ByteBuf, OpenResponse>> OPEN = REGISTRY.register("open", () -> OpenResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, BindingResponse>> BINDING = REGISTRY.register("binding", () -> BindingResponse.STREAM_CODEC);
}
