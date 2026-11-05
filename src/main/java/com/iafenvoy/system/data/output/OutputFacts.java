package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.store.Reward;

import java.util.List;

/**
 * Shared memory projections for results that report the same entitlement or prize from different modules.
 */
final class OutputFacts {
    private OutputFacts() {
    }

    static CardFact reward(String entitlementId, String state, PersistHolder<Reward> reward) {
        return new CardFact("reward", entitlementId, "", state, List.of(new CardFact.Reference(
                reward.id().toString(), reward.value().label().getString(), "", List.of(), 0)));
    }
}
