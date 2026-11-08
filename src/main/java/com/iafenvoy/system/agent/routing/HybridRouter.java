package com.iafenvoy.system.agent.routing;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.chat.ConversationStrategy;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.ModuleRegistry;
import com.iafenvoy.system.agent.storage.ConversationMemory;
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
        // Card actions retain their own authority; only plain text follows the selected conversation strategy.
        if (input != null) return this.direct(context, input, RouteSource.STRUCTURED);
        if (text.isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        return context.mode().route(new Routes(context, text));
    }

    private final class Routes implements ConversationStrategy.Routes {
        private final ExecutionContext context;
        private final String text;
        private List<ModelRoute.Candidate> hints = List.of();

        private Routes(ExecutionContext context, String text) {
            this.context = context;
            this.text = text;
        }

        @Override
        public RouteDecision local() {
            AgentAction command = HybridRouter.this.modules.commands().parse(this.text, this.context::available);
            if (command != null) return HybridRouter.this.direct(this.context, command, RouteSource.COMMAND);
            AgentAction explicit = HybridRouter.this.modules.explicit(this.text, this.context::available);
            if (explicit != null) return HybridRouter.this.direct(this.context, explicit, RouteSource.COMMAND);
            SemanticRouter.Attempt attempt;
            try {
                attempt = HybridRouter.this.semantic.match(this.context, this.text);
            } catch (Exception | LinkageError | AssertionError error) {
                this.context.ensureMemoryCurrent();
                attempt = SemanticRouter.Attempt.fallback("SEMANTIC_ERROR");
            }
            if (attempt.decision() != null && this.context.available(Identifier.tryParse(attempt.decision().moduleId()))
                    && HybridRouter.this.modules.semanticSelectable(Identifier.tryParse(attempt.decision().moduleId())))
                return attempt.decision();
            this.hints = attempt.hints().stream()
                    .filter(candidate -> HybridRouter.this.modules.modelSelectable(Identifier.tryParse(candidate.moduleId()))
                            && this.context.available(Identifier.tryParse(candidate.moduleId()))).toList();
            return this.fallback();
        }

        @Override
        public RouteDecision assisted() throws Exception {
            if (!this.context.externalServicesAllowed()) return this.fallback();
            // The decoder accepts exactly the advertised catalogue, including confirmation-only exclusions.
            List<ConversationMemory.Entry> history = this.context.store().read(this.context.player(), data -> ConversationMemory.recent(data, this.context.taskId(), 1600));
            try {
                ModelRoute route = this.context.model(this.context.prompt(SystemPrompts.ROUTING, Map.of("capabilities", CodecIO.write(ModelRoute.Capability.CATALOGUE, HybridRouter.this.modules.capabilities(this.context::available)))),
                        ModelRoute.Input.CODEC, new ModelRoute.Input(this.text, history, this.hints), HybridRouter.this.modules.modelCodec(this.context::available));
                return new RouteDecision(route.call(), RouteSource.LLM,
                        route.call() == null ? HybridRouter.this.modules.suggestions(this.text, this.hints, this.context::available) : List.of());
            } catch (AgentException error) {
                // Missing credentials or opt-out only disable model fallback, not registered local capabilities.
                if (!ALLOWED_FAILURES.contains(error.failure())) throw error;
                this.context.ensureMemoryCurrent();
                return this.fallback();
            }
        }

        private RouteDecision fallback() {
            return new RouteDecision(null, RouteSource.LOCAL_FALLBACK, HybridRouter.this.modules.suggestions(this.text, this.hints, this.context::available));
        }

        @Override
        public RouteDecision chat() {
            return HybridRouter.this.direct(this.context, new FreeChatInput(this.text), RouteSource.FREE_CHAT);
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
        if (!context.permissions().allowAi())
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
