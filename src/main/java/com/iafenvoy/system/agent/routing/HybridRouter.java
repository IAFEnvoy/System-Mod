package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.store.ConversationMemory;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.data.SelectionCardData;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Selects an entry point only; registered modules own syntax, examples and workflow execution.
 */
public final class HybridRouter implements AutoCloseable {
    private final ModuleRegistry modules;
    private final SemanticRouter semantic = new SemanticRouter();

    public HybridRouter(AgentEngine engine) {
        this.modules = engine.modules();
    }

    public void start() {
        this.modules.freeze();
        this.semantic.start(this.modules.profiles());
    }

    public RouteDecision route(ExecutionContext context, AgentAction input, String text) throws Exception {
        // Structured actions -> registered commands -> local read intent/cache -> one bounded LLM decision.
        if (input != null) return this.direct(context, input, RouteSource.STRUCTURED);
        if (text.isBlank()) throw new AgentException("INVALID_INPUT");
        AgentAction command = this.modules.commands().parse(text);
        if (command != null) return this.direct(context, command, RouteSource.COMMAND);
        AgentAction explicit = this.modules.explicit(text, context::available);
        if (explicit != null) return this.direct(context, explicit, RouteSource.COMMAND);
        SemanticRouter.Attempt attempt;
        try {
            attempt = this.semantic.match(context, text);
        } catch (Exception | LinkageError | AssertionError error) {
            context.ensureMemoryCurrent();
            attempt = SemanticRouter.Attempt.fallback("SEMANTIC_ERROR");
        }
        if (attempt.decision() != null && context.available(Identifier.tryParse(attempt.decision().moduleId()))
                && this.modules.semanticSelectable(Identifier.tryParse(attempt.decision().moduleId())))
            return attempt.decision();
        List<ModelRoute.Candidate> hints = attempt.hints().stream()
                .filter(candidate -> this.modules.modelSelectable(Identifier.tryParse(candidate.moduleId()))
                        && context.available(Identifier.tryParse(candidate.moduleId()))).toList();
        // The decoder accepts exactly the advertised catalogue, including confirmation-only exclusions.
        List<ConversationMemory.Entry> history = context.store().read(context.player(), data -> ConversationMemory.recent(data, context.taskId(), 1600));
        ModelRoute route = context.model(context.prompt(SystemPrompts.ROUTING, Map.of("capabilities", CodecIO.write(ModelRoute.Capability.CATALOGUE, this.modules.capabilities(context::available)))),
                ModelRoute.Input.CODEC, new ModelRoute.Input(text, history, hints), this.modules.modelCodec(context::available));
        return new RouteDecision(route.call(), RouteSource.LLM);
    }

    private RouteDecision direct(ExecutionContext context, AgentAction input, RouteSource source) {
        if (!context.available(input.id())) throw new AgentException("CAPABILITY_UNAVAILABLE");
        return new RouteDecision(input, source);
    }

    public void remember(ExecutionContext context, RouteDecision decision, String text) {
        if (!this.modules.semanticSelectable(Identifier.tryParse(decision.moduleId()))) return;
        try {
            this.semantic.remember(context, decision, text);
        } catch (Exception ignored) { /* An optional cache must not change a completed read result. */ }
    }

    public AgentResult clarify(ExecutionContext context) {
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.clarify", "", List.of(
                CardUpdate.show(SelectionCardData.shortcuts("system.result.clarify", "system.result.clarify", context.shortcuts()))));
    }

    public void clear(UUID player) {
        this.semantic.clear(player);
    }

    public RoutingDiagnostics diagnostics() {
        return this.semantic.diagnostics();
    }

    @Override
    public void close() {
        this.semantic.close();
    }
}
