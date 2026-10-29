package com.iafenvoy.saturn.runtime.resulting.failures;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.SimpleFailure;

/**
 * Why a payment cannot be made. A payment that cannot be made is a value, never an exception: the same definition
 * must be loadable and fail cleanly at the place that tries to spend it.
 *
 * <p>A dependent mod's own payment channel answers with its own constants, built the same way
 * ({@code SimpleFailure.of("...")}) and named under its own namespace.
 */
public final class CostFailure {
    /**
     * A number provider produced a value that is not a finite positive amount.
     */
    public static final Failure INVALID_AMOUNT = SimpleFailure.of("invalid_amount");
    /**
     * The entry needs a payer and none was supplied.
     */
    public static final Failure NO_PAYER = SimpleFailure.of("no_payer");
    /**
     * The context does not offer the channel this entry needs (no account, no position, no inventory).
     */
    public static final Failure NO_CHANNEL = SimpleFailure.of("no_channel");
    /**
     * The payer does not carry the matching items.
     */
    public static final Failure MISSING_ITEM = SimpleFailure.of("missing_item");
    /**
     * A script cost refused the payment.
     */
    public static final Failure SCRIPT_REJECTED = SimpleFailure.of("script_rejected");
    /**
     * The bill was already paid, so it can no longer be priced into, edited or paid again.
     */
    public static final Failure BILL_CLOSED = SimpleFailure.of("bill_closed");
    /**
     * A payment reached one of its own stores again while it was writing it.
     */
    public static final Failure REENTRANT = SimpleFailure.of("reentrant");
    /**
     * A real pass began and never answered: what it wrote before it stopped is not known, so it is not paid again.
     */
    public static final Failure UNCERTAIN = SimpleFailure.of("uncertain");

    private CostFailure() {
    }
}
