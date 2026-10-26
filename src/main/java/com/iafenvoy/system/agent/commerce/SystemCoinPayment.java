package com.iafenvoy.system.agent.commerce;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Reserved extension. Debit must be atomic and idempotent; false guarantees no debit occurred.
 */
public interface SystemCoinPayment {
    boolean available();

    CompletionStage<Boolean> debit(UUID player, long amount, String transactionId);

    SystemCoinPayment DISABLED = new SystemCoinPayment() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletionStage<Boolean> debit(UUID player, long amount, String transactionId) {
            return CompletableFuture.failedFuture(new IllegalStateException("System currency is not installed"));
        }
    };
}
