package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.signin.OfflineSignIn;
import com.iafenvoy.system.assistant.signin.SignIns;
import com.iafenvoy.system.assistant.tool.player.PlayerQueryTool;
import com.iafenvoy.system.assistant.tool.signin.SignInCreditTool;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.data.progress.SignInRules;
import com.iafenvoy.system.data.progression.ProgressionSettings;
import com.iafenvoy.system.data.store.SignInArchive;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.signin.SignInModule;
import com.iafenvoy.system.module.builtin.signin.SignInStatusModule;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;
import java.util.Optional;

public record SignInSystemModule(
        Optional<List<Either<ResourceKey<SignInRewardEntry>, TagKey<SignInRewardEntry>>>> rewards,
        ProgressionSettings offline, SignInRules rules) implements CatalogModule<SignInRewardEntry> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "signin");
    public static final MapCodec<SignInSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SIGNIN_REWARD).optionalFieldOf("rewards").forGetter(SignInSystemModule::rewards),
            ProgressionSettings.CODEC.optionalFieldOf("offline", ProgressionSettings.DEFAULT).forGetter(SignInSystemModule::offline),
            SignInRules.CODEC.optionalFieldOf("rules", SignInRules.DEFAULT).forGetter(SignInSystemModule::rules)
    ).apply(i, SignInSystemModule::new));
    public static final ModuleStorage<SignInDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "sign_in"), SignInDataStorage.class, SignInDataStorage.CODEC, SignInDataStorage::new);

    public SignInSystemModule {
        rewards = rewards.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<SignInRewardEntry>> catalog() {
        return SystemResourceKeys.SIGNIN_REWARD;
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
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput, ExecutionContext> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.get(ContextTypes.GAME)).tool());
        SignIns signIns = service(context);
        OfflineSignIn offline = new OfflineSignIn(context.get(ContextTypes.CATALOGS), this.offline);
        return List.of(new SignInModule(context.get(ContextTypes.CATALOGS), signIns, offline, this.rules, player,
                        new SignInCreditTool(context.get(ContextTypes.CATALOGS), signIns, offline, this.rules, context.id()).tool()),
                new SignInStatusModule(context.get(ContextTypes.CATALOGS), signIns, offline, this.rules));
    }

    public static SignIns service(ModuleContext context) {
        return context.sharedRuntime(SignIns.class, () -> new SignIns(context.engine(), new SignInArchive(
                context.get(ContextTypes.GAME).server().getWorldPath(LevelResource.ROOT).resolve(SystemMod.MOD_ID).resolve("signin"))));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return service(context);
    }
}
