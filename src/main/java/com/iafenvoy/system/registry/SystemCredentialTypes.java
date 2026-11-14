package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.credential.*;
import com.iafenvoy.system.config.credential.builtin.AliyunNlsCredential;
import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.credential.builtin.OpenAiCredential;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Credential implementations own their fields, codecs and editors. */
@SuppressWarnings("unused")
public final class SystemCredentialTypes {
    public static final DeferredRegister<MapCodec<? extends Credential>> REGISTRY = DeferredRegister.create(SystemResourceKeys.CREDENTIAL_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Credential>, MapCodec<DeepSeekCredential>> DEEPSEEK = REGISTRY.register("deepseek", () -> DeepSeekCredential.CODEC);
    public static final DeferredHolder<MapCodec<? extends Credential>, MapCodec<OpenAiCredential>> OPENAI = REGISTRY.register("openai", () -> OpenAiCredential.CODEC);
    public static final DeferredHolder<MapCodec<? extends Credential>, MapCodec<DashScopeCredential>> DASHSCOPE = REGISTRY.register("dashscope", () -> DashScopeCredential.CODEC);
    public static final DeferredHolder<MapCodec<? extends Credential>, MapCodec<AliyunNlsCredential>> ALIYUN_NLS = REGISTRY.register("aliyun_nls", () -> AliyunNlsCredential.CODEC);
}
