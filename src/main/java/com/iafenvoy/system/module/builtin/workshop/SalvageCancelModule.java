package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.assistant.workshop.Salvage;
import com.iafenvoy.system.data.input.SalvageCancelInput;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cancelling a read-only offer has no durable equipment or payment effect.
 */
public final class SalvageCancelModule extends AbstractAgentModule<SalvageCancelInput> {
    private final Salvage salvage;

    public SalvageCancelModule(Salvage salvage) {
        super(SalvageCancelInput.CODEC, "Discard my current salvage quote.", List.of(), true, false, ExecutionPolicy.quick(0, 0));
        this.salvage = salvage;
    }

    @Override
    public ModuleRouting<SalvageCancelInput> routing() {
        return ModuleRouting.<SalvageCancelInput>defaults().button(args -> new SalvageCancelInput(args.required("quote"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, SalvageCancelInput input) throws Exception {
        this.salvage.cancel(context, input.quote()).toCompletableFuture().get(context.remainingMillis(), TimeUnit.MILLISECONDS);
        return AgentResult.ok("system.result.salvage_cancelled");
    }
}
