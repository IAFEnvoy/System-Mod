package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.response.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemResponseTypes {
    public static final DeferredRegister<MapCodec<? extends AgentResponse>> REGISTRY = DeferredRegister.create(SystemResourceKeys.AGENT_RESPONSE_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<StateResponse>> STATE = REGISTRY.register("state", () -> StateResponse.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<HistoryResponse>> HISTORY = REGISTRY.register("history", () -> HistoryResponse.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<ReplyResponse>> REPLY = REGISTRY.register("reply", () -> ReplyResponse.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<NoticeResponse>> NOTICE = REGISTRY.register("notice", () -> NoticeResponse.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<OpenResponse>> OPEN = REGISTRY.register("open", () -> OpenResponse.CODEC);
    public static final DeferredHolder<MapCodec<? extends AgentResponse>, MapCodec<BindingResponse>> BINDING = REGISTRY.register("binding", () -> BindingResponse.CODEC);
}
