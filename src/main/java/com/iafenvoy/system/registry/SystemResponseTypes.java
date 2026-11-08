package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.response.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemResponseTypes {
    public static final DeferredRegister<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>> REGISTRY = DeferredRegister.create(SystemResourceKeys.AGENT_RESPONSE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<ByteBuf, StateResponse>> STATE = REGISTRY.register("state", () -> StateResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, HistoryResponse>> HISTORY = REGISTRY.register("history", () -> HistoryResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, ReplyResponse>> REPLY = REGISTRY.register("reply", () -> ReplyResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, NoticeResponse>> NOTICE = REGISTRY.register("notice", () -> NoticeResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<ByteBuf, OpenResponse>> OPEN = REGISTRY.register("open", () -> OpenResponse.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentResponse>, StreamCodec<RegistryFriendlyByteBuf, BindingResponse>> BINDING = REGISTRY.register("binding", () -> BindingResponse.STREAM_CODEC);
}
