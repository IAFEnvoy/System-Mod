package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.card.CardFact;

import java.util.List;

/**
 * Shared memory projections for results that report the same entitlement or prize from different modules.
 */
final class OutputFacts {
    private OutputFacts() {
    }

    static CardFact reward(String entitlementId, String state, Reward reward) {
        return new CardFact("reward", entitlementId, "", state, List.of(new CardFact.Reference(
                reward.id().toString(), reward.label(), "", List.of(), 0)));
    }
}
