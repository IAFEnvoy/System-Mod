package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.speech.quest.QuestNarration;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Server-local dispatcher. ModuleManager owns its lifetime; providers own native state and validity checks.
 */
public final class QuestService implements PlayerModule {
    private record Registration(Identifier owner, QuestProvider provider) {
    }

    private final AgentEngine engine;
    private final Map<Identifier, List<Registration>> providers = new LinkedHashMap<>();
    private final QuestSelectionCache selections = new QuestSelectionCache();

    public QuestService(AgentEngine engine) {
        this.engine = engine;
        engine.lifecycle((player, _) -> this.selections.invalidate(player));
    }

    public void register(Identifier owner, QuestProvider provider) {
        List<Registration> entries = this.providers.computeIfAbsent(provider.source().id(), _ -> new ArrayList<>());
        if (entries.stream().anyMatch(entry -> entry.owner().equals(owner)))
            throw new IllegalArgumentException("Duplicate quest source for module " + owner);
        if (entries.stream().anyMatch(entry -> entry.provider() != provider))
            throw new IllegalArgumentException("Conflicting quest provider " + provider.source().id());
        entries.add(new Registration(owner, provider));
    }

    /**
     * Providers also call this inside queued server-thread work, immediately before accessing native state.
     */
    public void checkAccess(ExecutionContext context, Identifier source) {
        if (this.providers.getOrDefault(source, List.of()).stream()
                .noneMatch(entry -> this.engine.moduleEnabled(context.player(), entry.owner())))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    private List<QuestProvider> enabled(ExecutionContext context) {
        return this.providers.values().stream().flatMap(entries -> entries.stream()
                        .filter(entry -> this.engine.moduleEnabled(context.player(), entry.owner())).findFirst().stream())
                .map(Registration::provider).toList();
    }

    private QuestKey key(ExecutionContext context, String value) {
        try {
            if (value.indexOf('#') >= 0) return QuestKey.parse(value);
            // Old cards used native IDs; they are safe only when there is one unambiguous enabled source.
            List<QuestProvider> enabled = this.enabled(context);
            if (enabled.size() == 1) return new QuestKey(enabled.getFirst().source().id(), value);
        } catch (IllegalArgumentException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
        throw new AgentException(AgentFailure.QUEST_AMBIGUOUS);
    }

    private QuestProvider provider(ExecutionContext context, QuestKey key) {
        this.checkAccess(context, key.source());
        return this.providers.get(key.source()).getFirst().provider();
    }

    private static <T, R> CompletionStage<List<R>> collect(List<T> sources, Function<T, CompletionStage<List<R>>> operation) {
        CompletionStage<List<R>> result = CompletableFuture.completedFuture(new ArrayList<>());
        for (T source : sources)
            result = result.thenCompose(values -> operation.apply(source).thenApply(next -> {
                values.addAll(next);
                return values;
            }));
        return result.thenApply(List::copyOf);
    }

    public CompletionStage<List<QuestBriefOutput>> query(ExecutionContext context, QuestQueryInput input) {
        List<QuestProvider> providers = this.enabled(context).stream().filter(provider -> switch (input.view()) {
            case ACTIVE -> true;
            case CLAIMABLE -> provider instanceof QuestProvider.Rewards;
            case SUBMITTABLE -> provider instanceof QuestProvider.Submission;
        }).toList();
        QuestQueryInput query = input;
        if (input.query().contains("#")) {
            QuestKey key = this.key(context, input.query());
            QuestProvider selected = this.provider(context, key);
            providers = providers.contains(selected) ? List.of(selected) : List.of();
            query = new QuestQueryInput(key.localId(), input.view());
        }
        QuestQueryInput selected = query;
        return collect(providers, provider -> provider.query(context, selected))
                .thenApply(quests -> this.number(context, quests, true));
    }

    public CompletionStage<List<QuestBriefOutput>> find(ExecutionContext context, QuestFindInput input) {
        List<QuestProvider> providers = this.enabled(context);
        QuestFindInput query = input;
        if (input.query().contains("#")) {
            QuestKey key = this.key(context, input.query());
            providers = List.of(this.provider(context, key));
            query = new QuestFindInput(key.localId());
        }
        QuestFindInput selected = query;
        return collect(providers, provider -> provider.find(context, selected)).thenApply(quests -> {
            List<QuestBriefOutput> exact = quests.stream().filter(quest -> quest.title().getString().equalsIgnoreCase(input.query().strip())).toList();
            List<QuestBriefOutput> matches = exact.isEmpty() ? quests : exact;
            // A unique match opens a detail card and must not replace the last displayed list.
            return this.number(context, matches, matches.size() != 1);
        });
    }

    private List<QuestBriefOutput> number(ExecutionContext context, List<QuestBriefOutput> quests, boolean remember) {
        for (QuestBriefOutput quest : quests) {
            QuestKey key = QuestKey.parse(quest.id());
            if (!key.source().equals(quest.source().id()))
                throw new IllegalArgumentException("Quest source does not match ID " + key);
            if (quest.revision().isBlank())
                throw new IllegalArgumentException("Missing quest selection revision " + key);
            this.checkAccess(context, key.source());
        }
        if (remember) this.selections.remember(context, quests);
        List<QuestBriefOutput> numbered = new ArrayList<>();
        for (QuestBriefOutput quest : quests) numbered.add(quest.withNumber(numbered.size() + 1));
        return List.copyOf(numbered);
    }

    public CompletionStage<QuestOutput> detail(ExecutionContext context, QuestDetailInput input) {
        if (!input.query().isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        QuestBriefOutput selected = input.index().map(index -> this.selections.select(context, index)).orElse(null);
        QuestKey key = this.key(context, selected == null ? input.questId() : selected.id());
        QuestDetailInput request = new QuestDetailInput("", Optional.empty(),
                key.toString(), input.rewardOffset(), input.choiceOffset(), input.descriptionOffset(), input.taskOffset());
        return this.provider(context, key).detail(context, request, selected == null ? Optional.empty() : Optional.of(selected.revision()));
    }

    public CompletionStage<QuestSubmitOutput> submit(ExecutionContext context, QuestSubmitInput input) {
        if (!input.query().isBlank())
            return this.find(context, new QuestFindInput(input.query())).thenCompose(matches -> {
                if (matches.isEmpty()) throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
                if (matches.size() != 1) throw new AgentException(AgentFailure.QUEST_AMBIGUOUS);
                return this.submit(context, new QuestSubmitInput("", Optional.empty(), matches.getFirst().id(), input.checkmarkOnly()));
            });
        QuestBriefOutput selected = input.index().map(index -> this.selections.select(context, index)).orElse(null);
        QuestKey key = this.key(context, selected == null ? input.questId() : selected.id());
        if (!(this.provider(context, key) instanceof QuestProvider.Submission submission))
            throw new AgentException(AgentFailure.UNSUPPORTED);
        return submission.submit(context, new QuestSubmitInput("", Optional.empty(), key.toString(), input.checkmarkOnly()),
                selected == null ? Optional.empty() : Optional.of(selected.revision()));
    }

    public CompletionStage<QuestClaimOutput> claim(ExecutionContext context, QuestClaimInput input) {
        QuestKey key = this.key(context, input.claimRef());
        if (!(this.provider(context, key) instanceof QuestProvider.Rewards rewards))
            throw new AgentException(AgentFailure.UNSUPPORTED);
        return rewards.claim(context, new QuestClaimInput(key.toString(), input.choice()));
    }

    public CompletionStage<QuestGuideOutput> guides(ExecutionContext context, QuestGuideInput input) {
        List<QuestProvider.Guides> sources = this.enabled(context).stream().filter(QuestProvider.Guides.class::isInstance)
                .map(QuestProvider.Guides.class::cast).toList();
        return collect(sources, source -> source.guides(context, input).thenApply(QuestGuideOutput::entries))
                .thenApply(entries -> new QuestGuideOutput(input.query(), entries));
    }

    public CompletionStage<QuestNarration.Quest> narration(ExecutionContext context, String id) {
        QuestKey key = this.key(context, id);
        if (!(this.provider(context, key) instanceof QuestProvider.Narration narration))
            throw new AgentException(AgentFailure.UNSUPPORTED);
        return narration.load(context, key.toString());
    }

    private static StarterGiftOutput gifts(List<StarterGiftOutput> outputs) {
        return new StarterGiftOutput(outputs.stream().flatMap(output -> output.gifts().stream()).toList(),
                outputs.stream().flatMap(output -> output.items().stream()).toList(),
                outputs.stream().flatMap(output -> output.icons().stream()).toList());
    }

    public CompletionStage<StarterGiftOutput> gifts(ExecutionContext context) {
        List<QuestProvider.StarterGifts> sources = this.enabled(context).stream().filter(QuestProvider.StarterGifts.class::isInstance)
                .map(QuestProvider.StarterGifts.class::cast).toList();
        return collect(sources, source -> source.gifts(context).thenApply(List::of)).thenApply(QuestService::gifts);
    }

    public CompletionStage<StarterGiftOutput> claimGifts(ExecutionContext context, StarterGiftInput input) {
        Map<QuestProvider.StarterGifts, List<String>> requests = new LinkedHashMap<>();
        // Authorize every requested source before the first write; never fall back to a different backend.
        for (String reference : input.claimRefs()) {
            QuestKey key = this.key(context, reference);
            if (!(this.provider(context, key) instanceof QuestProvider.StarterGifts source))
                throw new AgentException(AgentFailure.UNSUPPORTED);
            requests.computeIfAbsent(source, _ -> new ArrayList<>()).add(key.toString());
        }
        return collect(new ArrayList<>(requests.entrySet()), entry -> entry.getKey()
                .claimGifts(context, new StarterGiftInput(entry.getValue())).thenApply(List::of)).thenApply(QuestService::gifts);
    }

    @Override
    public void close() {
        this.selections.clear();
        this.providers.clear();
    }
}
