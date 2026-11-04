package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.util.PersistHolder;

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
