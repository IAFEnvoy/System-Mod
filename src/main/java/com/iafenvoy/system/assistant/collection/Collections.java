package com.iafenvoy.system.assistant.collection;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.progress.PlayerFacts;
import com.iafenvoy.system.data.catalog.CollectionDefinition;
import com.iafenvoy.system.data.input.CollectionQueryInput;
import com.iafenvoy.system.data.input.CollectionRegisterInput;
import com.iafenvoy.system.data.output.CollectionOutput;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.CollectionSystemModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.storage.CollectionDataStorage;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Server-authoritative collection registration; clients submit only a collection ID and never an item stack.
 */
public final class Collections {
    private record Fact(PlayerFact fact, List<PlayerFacts.Award> awards) {
    }

    private record Registration(CollectionDefinition definition, List<Fact> matches) {
    }

    private record Snapshot(Map<Identifier, Set<Identifier>> registered, Map<String, EarnedReward> rewards) {
    }

    private final AgentEngine engine;
    private final MinecraftTools game;
    private final CollectionSystemModule settings;

    public Collections(AgentEngine engine, MinecraftTools game, CollectionSystemModule settings) {
        this.engine = engine;
        this.game = game;
        this.settings = settings;
    }

    public CompletionStage<CollectionOutput> query(ExecutionContext context, CollectionQueryInput input) {
        Snapshot snapshot = context.store().read(context.player(), data -> {
            CollectionDataStorage storage = ModuleData.get(data, CollectionSystemModule.STORAGE);
            Map<Identifier, Set<Identifier>> registered = new LinkedHashMap<>();
            storage.registered().forEach((id, entries) -> registered.put(id, Set.copyOf(entries)));
            return new Snapshot(Map.copyOf(registered), ModuleData.get(data, CoreSystemModule.FACTS).rewards());
        });
        return this.game.onPlayer(context, player -> this.build(player, input, snapshot));
    }

    public CompletionStage<CollectionOutput> register(ExecutionContext context, CollectionRegisterInput input) {
        return this.game.onPlayer(context, player -> {
            CollectionDefinition definition = this.definition(player, input.collection());
            CatalogAccess.require(player, definition);
            List<Fact> matched = new ArrayList<>();
            Inventory inventory = player.getInventory();
            for (CollectionDefinition.Entry entry : definition.entries()) {
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (!stack.isEmpty() && entry.predicate().test(stack)) {
                        PlayerFact fact = new PlayerFact(PlayerFacts.COLLECTED, input.collection(), entry.id().toString(), "registered", System.currentTimeMillis());
                        matched.add(new Fact(fact, PlayerFacts.prepare(player, fact)));
                        break;
                    }
                }
            }
            return new Registration(definition, matched);
        }).thenCompose(registration -> this.engine.maintenance(() -> {
            context.store().update(context.player(), data -> {
                context.check();
                CollectionDataStorage storage = ModuleData.get(data, CollectionSystemModule.STORAGE);
                registration.matches().forEach(match -> {
                    Identifier entry = Identifier.parse(match.fact().subject());
                    if (storage.register(input.collection(), entry))
                        PlayerFacts.record(data, match.fact(), match.awards());
                });
                FactDataStorage facts = ModuleData.get(data, CoreSystemModule.FACTS);
                Set<Identifier> registered = storage.entries(input.collection());
                int count = (int) registration.definition().entries().stream().filter(entry -> registered.contains(entry.id())).count();
                registration.definition().milestones().stream().filter(milestone -> count >= milestone.target()).forEach(milestone ->
                        facts.earn(entitlement(input.collection(), milestone.id()), "collection",
                                new PersistHolder<>(milestone.id(), milestone.reward()), System.currentTimeMillis()));
                return null;
            });
            return this.query(context, new CollectionQueryInput(input.collection().toString(), 0));
        })).thenCompose(stage -> stage);
    }

    private CollectionOutput build(ServerPlayer player, CollectionQueryInput input, Snapshot snapshot) {
        Map<Identifier, Set<Identifier>> registered = snapshot.registered();
        List<Holder.Reference<CollectionDefinition>> definitions = player.registryAccess().lookupOrThrow(this.settings.catalog()).listElements()
                .filter(holder -> this.settings.allows(player.registryAccess(), holder.key().identifier()) && CatalogAccess.visible(player, holder.value()))
                .sorted(Comparator.comparing(holder -> holder.key().identifier())).toList();
        int offset = Math.clamp(input.offset(), 0, Math.max(0, ((definitions.size() - 1) / CollectionOutput.PAGE_SIZE) * CollectionOutput.PAGE_SIZE));
        if (input.collection().isBlank()) {
            int end = Math.min(definitions.size(), offset + CollectionOutput.PAGE_SIZE);
            List<CollectionOutput.Row> rows = new ArrayList<>();
            for (Holder.Reference<CollectionDefinition> holder : definitions.subList(Math.min(offset, definitions.size()), end)) {
                Identifier id = holder.key().identifier();
                int count = (int) holder.value().entries().stream().filter(entry -> registered.getOrDefault(id, Set.of()).contains(entry.id())).count();
                rows.add(new CollectionOutput.Row(id, holder.value().label(), holder.value().entries().getFirst().icon(), count >= holder.value().entries().size(), count, holder.value().entries().size()));
            }
            return new CollectionOutput("", Component.translatable("system.collection.title"), Component.empty(), rows,
                    0, definitions.size(), offset, end < definitions.size(), false, List.of());
        }
        Identifier id;
        try {
            id = Identifier.parse(input.collection());
        } catch (IllegalArgumentException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
        Holder.Reference<CollectionDefinition> holder = definitions.stream().filter(value -> value.key().identifier().equals(id)).findFirst().orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
        CollectionDefinition definition = holder.value();
        Set<Identifier> found = registered.getOrDefault(id, Set.of());
        int count = (int) definition.entries().stream().filter(entry -> found.contains(entry.id())).count();
        offset = Math.clamp(input.offset(), 0, Math.max(0, ((definition.entries().size() - 1) / CollectionOutput.PAGE_SIZE) * CollectionOutput.PAGE_SIZE));
        int end = Math.min(definition.entries().size(), offset + CollectionOutput.PAGE_SIZE);
        List<CollectionOutput.Row> rows = definition.entries().subList(Math.min(offset, definition.entries().size()), end).stream()
                .map(entry -> new CollectionOutput.Row(entry.id(), entry.label(), entry.icon(), found.contains(entry.id()), found.contains(entry.id()) ? 1 : 0, 1)).toList();
        return new CollectionOutput(id.toString(), definition.label(), definition.description(), rows, count, definition.entries().size(), offset,
                end < definition.entries().size(), !CatalogAccess.usable(player, definition), definition.milestones().stream()
                .<Component>map(milestone -> {
                    EarnedReward earned = snapshot.rewards().get(entitlement(id, milestone.id()));
                    String status = earned != null ? earned.transferred() ? "system.records.transferred" : "system.records.available"
                            : count >= milestone.target() ? "system.collection.reached" : "system.collection.not_reached";
                    return Component.translatable("system.collection.milestone", milestone.target(),
                            earned == null ? milestone.reward().label() : earned.reward().value().label(), Component.translatable(status));
                }).toList());
    }

    private static String entitlement(Identifier collection, Identifier milestone) {
        return "collection:" + PlayerFact.key(collection.toString(), milestone.toString());
    }

    private CollectionDefinition definition(ServerPlayer player, Identifier id) {
        if (!this.settings.allows(player.registryAccess(), id))
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return player.registryAccess().lookupOrThrow(this.settings.catalog()).get(id)
                .map(Holder.Reference::value).orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
    }
}
