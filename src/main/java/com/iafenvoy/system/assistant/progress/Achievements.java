package com.iafenvoy.system.assistant.progress;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.input.AchievementQueryInput;
import com.iafenvoy.system.data.output.AchievementsOutput;
import com.iafenvoy.system.data.progress.AchievementDefinition;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only views of trusted lifetime counters and already earned reward snapshots.
 */
public final class Achievements {
    private record Snapshot(Map<String, Long> counts, Map<String, EarnedReward> rewards) {
    }

    private Achievements() {
    }

    public static AchievementsOutput query(ExecutionContext execution, AchievementQueryInput input, CatalogAccess access) {
        Snapshot snapshot = execution.store().read(execution.player(), data -> {
            FactDataStorage storage = ModuleData.get(data, CoreSystemModule.FACTS);
            return new Snapshot(storage.counts(), storage.rewards());
        });
        return access.read(execution, player -> {
            List<AchievementsOutput.Entry> all = player.registryAccess().lookupOrThrow(SystemResourceKeys.ACHIEVEMENT).listElements()
                    .filter(holder -> CatalogAccess.visible(player, holder.value()) || snapshot.rewards().containsKey(AchievementDefinition.entitlement(holder.key().identifier())))
                    .sorted(Comparator.<Holder.Reference<AchievementDefinition>>comparingInt(holder -> holder.value().order()).reversed().thenComparing(holder -> holder.key().identifier()))
                    .map(holder -> {
                        AchievementDefinition value = holder.value();
                        String id = AchievementDefinition.entitlement(holder.key().identifier());
                        EarnedReward earned = snapshot.rewards().get(id);
                        long count = earned == null ? Math.min(value.target(), snapshot.counts().getOrDefault(value.counter(), 0L)) : value.target();
                        Reward reward = earned == null ? value.reward() : earned.reward().value();
                        return new AchievementsOutput.Entry(value.label(), value.description(), value.series(), CardBuilder.join(reward.stacks().stream().map(SpaceStack::label).toList(), " · "),
                                count, value.target(), earned != null, earned != null && earned.transferred(), Optional.ofNullable(earned).map(EarnedReward::id));
                    }).toList();
            int start = Math.clamp(input.offset() / AchievementsOutput.PAGE_SIZE, 0, Math.max(0, all.size() - 1) / AchievementsOutput.PAGE_SIZE) * AchievementsOutput.PAGE_SIZE;
            LinkedHashMap<Component, int[]> series = new LinkedHashMap<>();
            for (AchievementsOutput.Entry entry : all) {
                int[] counts = series.computeIfAbsent(entry.series(), _ -> new int[2]);
                counts[0] += entry.completed() ? 1 : 0;
                counts[1]++;
            }
            List<AchievementsOutput.Entry> page = all.subList(start, Math.min(all.size(), start + AchievementsOutput.PAGE_SIZE));
            Set<Component> pageSeries = page.stream().map(AchievementsOutput.Entry::series).collect(Collectors.toSet());
            return new AchievementsOutput(page, start, all.size(),
                    (int) all.stream().filter(AchievementsOutput.Entry::completed).count(), series.entrySet().stream()
                    .filter(entry -> pageSeries.contains(entry.getKey()))
                    .map(entry -> (Component) Component.translatable("system.achievements.series_progress", entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList());
        });
    }
}
