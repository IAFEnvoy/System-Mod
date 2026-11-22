package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.quest.Commissions;
import com.iafenvoy.system.assistant.quest.QuestWorkflows;
import com.iafenvoy.system.command.CommissionCommands;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.CommissionDefinition;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.storage.CommissionDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record CommissionSystemModule(
        Optional<List<Either<ResourceKey<CommissionDefinition>, TagKey<CommissionDefinition>>>> commissions,
        int capacity) implements CatalogModule<CommissionDefinition> {
    public static final Identifier ID = Identifier.parse("system:commission");
    public static final MapCodec<CommissionSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.COMMISSION).optionalFieldOf("commissions").forGetter(CommissionSystemModule::commissions),
            Codec.intRange(1, 128).optionalFieldOf("capacity", 16).forGetter(CommissionSystemModule::capacity)
    ).apply(i, CommissionSystemModule::new));
    public static final ModuleStorage<CommissionDataStorage> STORAGE = new ModuleStorage<>(ID, ID, CommissionDataStorage.class, CommissionDataStorage.CODEC, CommissionDataStorage::new);

    public CommissionSystemModule {
        commissions = commissions.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<CommissionDefinition>> catalog() {
        return SystemResourceKeys.COMMISSION;
    }

    @Override
    public Optional<List<Either<ResourceKey<CommissionDefinition>, TagKey<CommissionDefinition>>>> options() {
        return this.commissions;
    }

    @Override
    public MapCodec<CommissionSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return Commissions.service(context);
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        Commissions service = Commissions.service(context);
        service.register(context.id(), this);
        return QuestWorkflows.install(context, service);
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        CommissionCommands.register(root);
    }
}
