package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.assistant.jobs.OnlineJobs;
import com.iafenvoy.system.assistant.tool.jobs.*;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.builtin.jobs.*;
import com.iafenvoy.system.module.builtin.storage.OnlineJobsDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record OnlineJobsSystemModule(Optional<List<Either<ResourceKey<OnlineJobEntry>, TagKey<OnlineJobEntry>>>> jobs) implements CatalogModule<OnlineJobEntry> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "online_jobs");
    public static final MapCodec<OnlineJobsSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.ONLINE_JOBS).optionalFieldOf("jobs").forGetter(OnlineJobsSystemModule::jobs)
    ).apply(i, OnlineJobsSystemModule::new));
    public static final ModuleStorage<OnlineJobsDataStorage> STORAGE = new ModuleStorage<>(ID, ID, OnlineJobsDataStorage.class, OnlineJobsDataStorage.CODEC, OnlineJobsDataStorage::new);

    public OnlineJobsSystemModule {
        jobs = jobs.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<OnlineJobEntry>> catalog() {
        return SystemResourceKeys.ONLINE_JOBS;
    }

    @Override
    public Optional<List<Either<ResourceKey<OnlineJobEntry>, TagKey<OnlineJobEntry>>>> options() {
        return this.jobs;
    }

    @Override
    public MapCodec<OnlineJobsSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        OnlineJobs service = new OnlineJobs(context.get(ContextTypes.GAME).server().registryAccess(), this);
        return List.of(new OnlineJobsModule(new OnlineJobsQueryTool(service).tool()), new JobStartModule(new JobStartTool(service).tool()),
                new JobClaimModule(new JobClaimTool(service).tool()), new JobCancelModule(new JobCancelTool(service).tool()));
    }
}
