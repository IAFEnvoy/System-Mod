package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.tool.player.PlayerQueryTool;
import com.iafenvoy.system.assistant.tool.signin.SignInCreditTool;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.builtin.signin.SignInModule;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record SignInSystemModule(
        Optional<List<Either<ResourceKey<SignInRewardEntry>, TagKey<SignInRewardEntry>>>> rewards) implements CatalogModule<SignInRewardEntry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "signin");
    public static final MapCodec<SignInSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SIGNIN_REWARDS).optionalFieldOf("rewards").forGetter(SignInSystemModule::rewards)
    ).apply(i, SignInSystemModule::new));
    public static final ModuleStorage<SignInDataStorage> STORAGE = new ModuleStorage<>(ID, ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "sign_in"), SignInDataStorage.class, SignInDataStorage.CODEC, SignInDataStorage::new);

    public SignInSystemModule {
        rewards = rewards.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<SignInRewardEntry>> catalog() {
        return SystemResourceKeys.SIGNIN_REWARDS;
    }

    @Override
    public Optional<List<Either<ResourceKey<SignInRewardEntry>, TagKey<SignInRewardEntry>>>> options() {
        return this.rewards;
    }

    @Override
    public MapCodec<SignInSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.get(ContextTypes.GAME)).tool());
        return List.of(new SignInModule(context.get(ContextTypes.CATALOGS), player, new SignInCreditTool(context.get(ContextTypes.CATALOGS)).tool()));
    }
}
