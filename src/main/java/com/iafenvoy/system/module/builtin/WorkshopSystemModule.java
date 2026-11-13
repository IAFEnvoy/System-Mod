package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.assistant.tool.workshop.RepairConfirmTool;
import com.iafenvoy.system.assistant.tool.workshop.RepairQuoteTool;
import com.iafenvoy.system.assistant.workshop.Workshop;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.workshop.RepairConfirmModule;
import com.iafenvoy.system.module.builtin.workshop.RepairQuoteModule;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record WorkshopSystemModule(List<Cost> costs) implements SharedModule {
    public static final MapCodec<WorkshopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Cost.CODEC.listOf().fieldOf("costs").forGetter(WorkshopSystemModule::costs)
    ).apply(i, WorkshopSystemModule::new));

    public WorkshopSystemModule {
        costs = List.copyOf(costs);
    }

    @Override
    public MapCodec<WorkshopSystemModule> codec() {
        return CODEC;
    }

    private Workshop service(ModuleContext context) {
        return context.sharedRuntime(Workshop.class, () -> new Workshop(context.engine(), context.get(ContextTypes.GAME)));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return this.service(context);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        Workshop workshop = this.service(context);
        return List.of(new RepairQuoteModule(new RepairQuoteTool(workshop, context.id(), this.costs).tool()),
                new RepairConfirmModule(new RepairConfirmTool(workshop).tool()));
    }
}
