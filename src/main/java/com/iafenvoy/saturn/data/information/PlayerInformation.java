package com.iafenvoy.saturn.data.information;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.model.InformationContext;
import com.iafenvoy.saturn.data.information.model.InformationDocument;
import com.iafenvoy.saturn.data.information.model.InformationEntry;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Evaluates rows and group selections exclusively from the supplied view or context's datapack registries.
 * No definitions are registered or cached here.
 */
public final class PlayerInformation {
    private PlayerInformation() {
    }

    public static List<Holder<InformationGroup>> groups(PlayerView view, InformationContext context) {
        return definitions(view, context, SaturnResourceKeys.INFORMATION_GROUP)
                .<Holder<InformationGroup>>map(holder -> holder).sorted(InformationGroup.ORDER).toList();
    }

    public static List<Holder<InformationGroup>> groups(PlayerView view) {
        return groups(view, InformationContext.DEFAULT);
    }

    public static InformationDocument collectAll(PlayerView view) {
        return collectAll(view, InformationContext.DEFAULT);
    }

    public static InformationDocument collectAll(PlayerView view, InformationContext context) {
        return collectSelected(view, context, Set.of(), true);
    }

    public static InformationDocument collect(PlayerView view, Holder<InformationGroup> group) {
        return collect(view, InformationContext.DEFAULT, group);
    }

    public static InformationDocument collect(PlayerView view, InformationContext context, Holder<InformationGroup> group) {
        return collect(view, context, Set.of(group));
    }

    /**
     * An empty selection collects no rows; use {@link #collectAll(PlayerView, InformationContext)} to collect all.
     */
    public static InformationDocument collect(PlayerView view, InformationContext context, Set<Holder<InformationGroup>> groups) {
        return collectSelected(view, context, groups, false);
    }

    private static InformationDocument collectSelected(PlayerView view, InformationContext context, Set<Holder<InformationGroup>> selected, boolean all) {
        Optional<HolderLookup.RegistryLookup<InformationRow>> rows = lookup(view, context, SaturnResourceKeys.INFORMATION_ROW);
        if (rows.isEmpty() || !all && selected.isEmpty()) return new InformationDocument(List.of());

        SetMultimap<Holder<InformationRow>, Holder<InformationGroup>> memberships = HashMultimap.create();
        definitions(view, context, SaturnResourceKeys.INFORMATION_GROUP).forEach(group -> RegistryCodecs.resolve(group.value().rows(), rows.get()).forEach(row -> memberships.put(row, group)));

        InformationEvaluation evaluation = new InformationEvaluation(view, context);
        List<InformationEntry> result = new ArrayList<>();
        rows.get().listElements()
                .filter(row -> all || memberships.get(row).stream().anyMatch(selected::contains))
                .forEach(row -> {
                    try {
                        InformationEntry.collect(row, memberships.get(row), evaluation.forRow()).ifPresent(result::add);
                    } catch (RuntimeException failure) {
                        Saturn.LOGGER.warn("Failed to collect player information row {}", row.key().location(), failure);
                    }
                });
        result.sort(!all && selected.size() == 1 ? InformationEntry.ORDER : InformationEntry.GROUP_ORDER);
        return new InformationDocument(result);
    }

    private static <T> Optional<HolderLookup.RegistryLookup<T>> lookup(PlayerView view, InformationContext context, ResourceKey<Registry<T>> key) {
        if (context.ops(view) instanceof RegistryOps<?> ops && ops.owner(key).orElse(null) instanceof HolderLookup.RegistryLookup<?> lookup) {
            @SuppressWarnings("unchecked") HolderLookup.RegistryLookup<T> typed = (HolderLookup.RegistryLookup<T>) lookup;
            return Optional.of(typed);
        }
        return Optional.empty();
    }

    private static <T> Stream<Holder.Reference<T>> definitions(PlayerView view, InformationContext context, ResourceKey<Registry<T>> key) {
        return lookup(view, context, key).stream().flatMap(HolderLookup::listElements);
    }
}
