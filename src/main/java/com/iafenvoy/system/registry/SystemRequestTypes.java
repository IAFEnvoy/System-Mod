package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.request.ChatRequest;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.request.InvokeRequest;
import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemRequestTypes {
    public static final DeferredRegister<MapCodec<? extends AgentRequest>> REGISTRY = DeferredRegister.create(SystemResourceKeys.AGENT_REQUEST_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AgentRequest>, MapCodec<ChatRequest>> CHAT = REGISTRY.register("chat", () -> ChatRequest.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentRequest>, MapCodec<InvokeRequest>> INVOKE = REGISTRY.register("invoke", () -> InvokeRequest.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentRequest>, MapCodec<ControlRequest>> CONTROL = REGISTRY.register("control", () -> ControlRequest.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentRequest>, MapCodec<PreferencesRequest>> PREFERENCES = REGISTRY.register("preferences", () -> PreferencesRequest.CODEC);
}
