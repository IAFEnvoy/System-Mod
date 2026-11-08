package com.iafenvoy.system.agent.routing;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.ModuleRegistry;
import com.iafenvoy.system.agent.storage.ConversationMemory;
import com.iafenvoy.system.config.file.AiAccessConfig;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.data.SelectionCardData;
import com.iafenvoy.system.data.input.FreeChatInput;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Selects an entry point only; registered modules own syntax, examples and workflow execution.
 */
public final class HybridRouter implements AutoCloseable {
    private static final Set<Failure> ALLOWED_FAILURES = Set.of(AgentFailure.MODEL_NOT_CONFIGURED, AgentFailure.MODEL_DISABLED, AgentFailure.AI_FORBIDDEN);
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
        if (text.isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        AgentAction command = this.modules.commands().parse(text, context::available);
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
        // Free conversation replaces the model intent pass; local commands and confident reads stay cheap.
        if (context.freeChatAllowed()) return this.direct(context, new FreeChatInput(text), RouteSource.FREE_CHAT);
        List<ModelRoute.Candidate> hints = attempt.hints().stream()
                .filter(candidate -> this.modules.modelSelectable(Identifier.tryParse(candidate.moduleId()))
                        && context.available(Identifier.tryParse(candidate.moduleId()))).toList();
        // The decoder accepts exactly the advertised catalogue, including confirmation-only exclusions.
        List<ConversationMemory.Entry> history = context.store().read(context.player(), data -> ConversationMemory.recent(data, context.taskId(), 1600));
        try {
            ModelRoute route = context.model(context.prompt(SystemPrompts.ROUTING, Map.of("capabilities", CodecIO.write(ModelRoute.Capability.CATALOGUE, this.modules.capabilities(context::available)))),
                    ModelRoute.Input.CODEC, new ModelRoute.Input(text, history, hints), this.modules.modelCodec(context::available));
            return new RouteDecision(route.call(), RouteSource.LLM,
                    route.call() == null ? this.modules.suggestions(text, hints, context::available) : List.of());
        } catch (AgentException error) {
            // Missing credentials or opt-out only disable model fallback, not registered local capabilities.
            if (!ALLOWED_FAILURES.contains(error.failure())) throw error;
            context.ensureMemoryCurrent();
            return new RouteDecision(null, RouteSource.LOCAL_FALLBACK, this.modules.suggestions(text, hints, context::available));
        }
    }

    private RouteDecision direct(ExecutionContext context, AgentAction input, RouteSource source) {
        if (!context.available(input.id())) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return new RouteDecision(input, source);
    }

    public void remember(ExecutionContext context, RouteDecision decision, String text) {
        if (!this.modules.semanticSelectable(Identifier.tryParse(decision.moduleId()))) return;
        try {
            this.semantic.remember(context, decision, text);
        } catch (Exception ignored) { /* An optional cache must not change a completed read result. */ }
    }

    public AgentResult clarify(ExecutionContext context, RouteDecision decision) {
        String key = decision.source() == RouteSource.LOCAL_FALLBACK ? "system.result.clarify_local" : "system.result.clarify";
        if (!AiAccessConfig.INSTANCE.get().permissions(context.player()).allowAi())
            key = "system.result.clarify_admin_disabled";
        return new AgentResult(AgentResult.Status.SUCCEEDED, key, "", List.of(
                CardUpdate.show(SelectionCardData.shortcuts(key, key, decision.choices().isEmpty() ? context.shortcuts() : decision.choices()))));
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
