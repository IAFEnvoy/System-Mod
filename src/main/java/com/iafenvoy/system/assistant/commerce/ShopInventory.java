package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.output.ShopPurchaseLimitOutput;
import com.iafenvoy.system.data.output.ShopRotationOutput;
import com.iafenvoy.system.module.merge.MergeContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * One aggregate view is used by browsing, quoting and payment; there is no secondary stock lookup path.
 */
public final class ShopInventory {
    private static final int MAX_ENTRIES = 4096;
    private final MergeContext<ShopContribution> context;

    public ShopInventory(MergeContext<ShopContribution> context) {
        this.context = context;
    }

    public List<ShopOffer> offers(UUID player, CommerceCatalog catalog) {
        Map<Identifier, ShopOffer> offers = new TreeMap<>();
        Map<Identifier, Set<String>> revisions = new HashMap<>();
        for (MergeContext.Member<ShopContribution> member : this.context.enabled(player)) {
            try (Stream<? extends ShopOffer> source = member.contribution().offers(player, catalog)) {
                Iterator<? extends ShopOffer> iterator = source.iterator();
                int read = 0;
                while (iterator.hasNext()) {
                    if (++read > MAX_ENTRIES) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
                    ShopOffer offer = Objects.requireNonNull(iterator.next());
                    Objects.requireNonNull(offer.id());
                    Objects.requireNonNull(offer.revision());
                    if (!offer.value().enabled()) continue;
                    if (!(offer instanceof ShopOffer.Fixed)) offer = new ShopOffer.Sourced(member.definition(), offer);
                    if (offer instanceof ShopOffer.Fixed)
                        revisions.computeIfAbsent(offer.id(), _ -> new TreeSet<>()).add(offer.revision());
                    ShopOffer previous = offers.putIfAbsent(offer.id(), offer);
                    // Only aliases of the same registry entry share identity and its purchase counter.
                    if (previous != null && !(previous instanceof ShopOffer.Fixed first && offer instanceof ShopOffer.Fixed second
                            && first.holder().equals(second.holder())))
                        throw new IllegalArgumentException("Conflicting shop offer " + offer.id());
                    if (offers.size() > MAX_ENTRIES) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
                }
            }
            this.context.require(player, HolderHelper.id(member.definition()));
        }
        // Hash the complete sorted set once, so three or more aliases are independent of member order.
        offers.replaceAll((id, offer) -> {
            Set<String> versions = revisions.get(id);
            if (!(offer instanceof ShopOffer.Fixed fixed) || versions.size() == 1) return offer;
            String aliases = String.join("\0", versions);
            return new ShopOffer.Fixed(fixed.holder(), UUID.nameUUIDFromBytes(aliases.getBytes(StandardCharsets.UTF_8)).toString());
        });
        return List.copyOf(offers.values());
    }

    public boolean searchOnly(UUID player, ShopTrade trade) {
        return this.context.enabled(player).stream().anyMatch(member -> member.contribution().searchOnly(trade));
    }

    public List<ShopRotationOutput> rotations(UUID player, CommerceCatalog catalog, ShopTrade trade) {
        return this.context.enabled(player).stream().flatMap(member -> member.contribution().rotations(player, catalog, trade).stream()).toList();
    }

    public List<ShopOffer> requested(ServerPlayer player, CommerceCatalog fixed, ShopRequest request,
                                     Map<Identifier, ShopPurchaseLimitOutput> limits) {
        Map<Identifier, ShopOffer> offers = new TreeMap<>();
        fixed.offers().forEach(offer -> offers.put(offer.id(), offer));
        int generated = 0;
        for (MergeContext.Member<ShopContribution> member : this.context.enabled(player.getUUID())) {
            List<? extends ShopOffer> additions = member.contribution().requested(player, fixed, request, limits);
            if (additions.size() > ShopRequest.MAX_RESULTS + 1) throw new AgentException(AgentFailure.BUDGET_EXCEEDED);
            for (ShopOffer offer : additions) {
                if (!offer.value().enabled()) continue;
                if (!offer.searchOnly()) throw new IllegalArgumentException("Requested stock must be search-only");
                if (offers.putIfAbsent(offer.id(), new ShopOffer.Sourced(member.definition(), offer)) != null)
                    throw new IllegalArgumentException("Conflicting shop offer " + offer.id());
                if (++generated >= ShopRequest.MAX_RESULTS + 1) break;
            }
            this.context.require(player.getUUID(), HolderHelper.id(member.definition()));
            if (generated == ShopRequest.MAX_RESULTS + 1) break;
        }
        return List.copyOf(offers.values());
    }
}
