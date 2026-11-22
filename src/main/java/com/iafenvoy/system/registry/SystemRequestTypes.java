package com.iafenvoy.system.registry;

import com.iafenvoy.assistant.registry.AssistantResourceKeys;
import com.iafenvoy.assistant.request.AgentRequest;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.request.CardActionRequest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemRequestTypes {
    public static final DeferredRegister<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>> REGISTRY = DeferredRegister.create(AssistantResourceKeys.AGENT_REQUEST_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<StreamCodec<? super RegistryFriendlyByteBuf, ? extends AgentRequest>, StreamCodec<ByteBuf, CardActionRequest>> CARD_ACTION = REGISTRY.register("card_action", () -> CardActionRequest.STREAM_CODEC);

}
