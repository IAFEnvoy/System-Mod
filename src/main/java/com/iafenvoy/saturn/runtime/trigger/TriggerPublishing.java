package com.iafenvoy.saturn.runtime.trigger;

import com.iafenvoy.saturn.data.trigger.TriggerContext;
import com.iafenvoy.saturn.data.trigger.TriggerSignal;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

/**
 * The single entry every event bridge publishes through. It owns the one optimization the dispatcher relies
 * on: a signal nothing listens to is not built at all, which is what keeps per-tick signals affordable.
 */
public final class TriggerPublishing {
    private TriggerPublishing() {
    }

    public static void publish(ResourceLocation signalType, LivingEntity actor, FormulaContext context) {
        try (ProfilerScope scope = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            publish(signalType, actor, context, ignored -> {
            });
        }
    }

    public static void publish(ResourceLocation signalType, LivingEntity actor, FormulaContext context, Consumer<TriggerContext> enrich) {
        try (ProfilerScope scope = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            // Nothing to react with, so nothing to build: the tick signal is published by every living entity on
            // every tick, and only the entities something listens to pay for a context.
            if (!TriggerDispatcher.hasListener(signalType)) return;
            TriggerContext triggerContext = new TriggerContext()
                    .actor(actor)
                    .level(actor.level())
                    .formula(context);
            enrich.accept(triggerContext);
            TriggerDispatcher.publish(new TriggerSignal(signalType, triggerContext, null, actor.level().getGameTime()));
        }
    }
}
