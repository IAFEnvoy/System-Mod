package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.tool.workshop.RepairConfirmTool;
import com.iafenvoy.system.assistant.tool.workshop.RepairQuoteTool;
import com.iafenvoy.system.assistant.workshop.Salvage;
import com.iafenvoy.system.assistant.workshop.Workshop;
import com.iafenvoy.system.command.WorkshopCommands;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.SalvageRule;
import com.iafenvoy.system.data.input.SalvageConfirmInput;
import com.iafenvoy.system.data.input.SalvageQuoteInput;
import com.iafenvoy.system.data.output.SalvageQuoteOutput;
import com.iafenvoy.system.data.output.SalvageReceiptOutput;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.WorkshopDataStorage;
import com.iafenvoy.system.module.builtin.workshop.*;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record WorkshopSystemModule(List<Cost> costs,
                                   List<Either<ResourceKey<SalvageRule>, TagKey<SalvageRule>>> salvage) implements CatalogModule<SalvageRule> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "workshop");
    public static final ModuleStorage<WorkshopDataStorage> STORAGE = new ModuleStorage<>(ID, ID, WorkshopDataStorage.class, WorkshopDataStorage.CODEC, WorkshopDataStorage::new);
    public static final MapCodec<WorkshopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Cost.CODEC.listOf().fieldOf("costs").forGetter(WorkshopSystemModule::costs),
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SALVAGE_RULE).optionalFieldOf("salvage", List.of()).forGetter(WorkshopSystemModule::salvage)
    ).apply(i, WorkshopSystemModule::new));

    public WorkshopSystemModule {
        costs = List.copyOf(costs);
        salvage = List.copyOf(salvage);
    }

    @Override
    public ResourceKey<Registry<SalvageRule>> catalog() {
        return SystemResourceKeys.SALVAGE_RULE;
    }

    @Override
    public Optional<List<Either<ResourceKey<SalvageRule>, TagKey<SalvageRule>>>> options() {
        return Optional.of(this.salvage);
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        WorkshopCommands.register(root);
    }

    @Override
    public MapCodec<WorkshopSystemModule> codec() {
        return CODEC;
    }

    private Workshop service(ModuleContext context) {
        return context.sharedRuntime(Workshop.class, () -> new Workshop(context.engine(), context.get(ContextTypes.GAME), this.salvage(context)));
    }

    private Salvage salvage(ModuleContext context) {
        return context.sharedRuntime(Salvage.class, () -> new Salvage(context.engine(), context.get(ContextTypes.GAME)));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return this.service(context);
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        Workshop workshop = this.service(context);
        Salvage salvage = this.salvage(context);
        ToolRegistry.Tool<SalvageQuoteInput, SalvageQuoteOutput, ExecutionContext> quote = context.tool(BuiltinTools.SALVAGE_QUOTE,
                () -> new ToolRegistry.Tool<SalvageQuoteInput, SalvageQuoteOutput, ExecutionContext>(BuiltinTools.SALVAGE_QUOTE, false, true, false, (execution, _) -> salvage.quote(execution, context.id(), this))
                        .forReasoning("Quote salvaging my single main-hand item. This only shows exact outputs; the player must click confirm to destroy it."));
        ToolRegistry.Tool<SalvageConfirmInput, SalvageReceiptOutput, ExecutionContext> confirm = context.tool(BuiltinTools.SALVAGE_CONFIRM,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SALVAGE_CONFIRM, true, true, false, salvage::confirm));
        return List.of(new RepairQuoteModule(new RepairQuoteTool(workshop, context.id(), this.costs).tool()),
                new RepairConfirmModule(new RepairConfirmTool(workshop).tool()), new SalvageQuoteModule(quote),
                new SalvageConfirmModule(confirm), new SalvageCancelModule(salvage), new SalvageHistoryModule(salvage));
    }
}
