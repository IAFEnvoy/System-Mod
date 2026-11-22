package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.collection.Collections;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.CollectionDefinition;
import com.iafenvoy.system.data.input.CollectionQueryInput;
import com.iafenvoy.system.data.input.CollectionRegisterInput;
import com.iafenvoy.system.data.output.CollectionOutput;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.builtin.collection.CollectionQueryModule;
import com.iafenvoy.system.module.builtin.collection.CollectionRegisterModule;
import com.iafenvoy.system.module.builtin.storage.CollectionDataStorage;
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

public record CollectionSystemModule(
        Optional<List<Either<ResourceKey<CollectionDefinition>, TagKey<CollectionDefinition>>>> collections) implements CatalogModule<CollectionDefinition> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "collection");
    public static final MapCodec<CollectionSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.COLLECTION).optionalFieldOf("collections").forGetter(CollectionSystemModule::collections)
    ).apply(i, CollectionSystemModule::new));
    public static final ModuleStorage<CollectionDataStorage> STORAGE = new ModuleStorage<>(ID, ID, CollectionDataStorage.class, CollectionDataStorage.CODEC, CollectionDataStorage::new);

    public CollectionSystemModule {
        collections = collections.map(List::copyOf);
    }

    @Override
    public MapCodec<CollectionSystemModule> codec() {
        return CODEC;
    }

    @Override
    public ResourceKey<Registry<CollectionDefinition>> catalog() {
        return SystemResourceKeys.COLLECTION;
    }

    @Override
    public Optional<List<Either<ResourceKey<CollectionDefinition>, TagKey<CollectionDefinition>>>> options() {
        return this.collections;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        Collections service = new Collections(context.engine(), context.get(ContextTypes.GAME), this);
        ToolRegistry.Tool<CollectionQueryInput, CollectionOutput, ExecutionContext> query = new ToolRegistry.Tool<>(BuiltinTools.COLLECTION_QUERY, false, true, false, service::query);
        ToolRegistry.Tool<CollectionRegisterInput, CollectionOutput, ExecutionContext> register = new ToolRegistry.Tool<>(BuiltinTools.COLLECTION_REGISTER, true, true, false, service::register);
        return List.of(new CollectionQueryModule(query), new CollectionRegisterModule(register));
    }
}
