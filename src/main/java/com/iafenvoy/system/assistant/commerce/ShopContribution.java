package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.output.ShopPurchaseLimitOutput;
import com.iafenvoy.system.data.output.ShopRotationOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Read immutable/cached stock on either caller thread; world access, I/O and generation belong in member runtimes.
 */
@FunctionalInterface
public interface ShopContribution {
    Stream<? extends ShopOffer> offers(UUID player, CommerceCatalog catalog);

    /**
     * Server-thread only. Fixed quotas were read on a worker; return at most MAX_RESULTS + 1 matches.
     */
    default List<? extends ShopOffer> requested(ServerPlayer player, CommerceCatalog fixed, ShopRequest request,
                                                Map<Identifier, ShopPurchaseLimitOutput> limits) {
        return List.of();
    }

    default boolean searchOnly(ShopTrade trade) {
        return false;
    }

    default List<ShopRotationOutput> rotations(UUID player, CommerceCatalog catalog, ShopTrade trade) {
        return List.of();
    }
}
