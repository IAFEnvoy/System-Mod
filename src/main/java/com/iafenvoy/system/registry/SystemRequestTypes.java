package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.request.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemRequestTypes {
    public static final DeferredRegister<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>> REGISTRY = DeferredRegister.create(SystemResourceKeys.AGENT_REQUEST_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, CardActionRequest>> CARD_ACTION = REGISTRY.register("card_action", () -> CardActionRequest.STREAM_CODEC);

    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, ChatRequest>> CHAT = REGISTRY.register("chat", () -> ChatRequest.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, InvokeRequest>> INVOKE = REGISTRY.register("invoke", () -> InvokeRequest.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, ControlRequest>> CONTROL = REGISTRY.register("control", () -> ControlRequest.STREAM_CODEC);
    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, PreferencesRequest>> PREFERENCES = REGISTRY.register("preferences", () -> PreferencesRequest.STREAM_CODEC);
}
