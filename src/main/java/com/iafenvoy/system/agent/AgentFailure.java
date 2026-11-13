package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.system.SystemMod;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Built-in refusal values; only provider adapters and persisted receipts decode their string names.
 */
public enum AgentFailure implements Failure {
    INVALID_INPUT, UNSUPPORTED, NOT_AUTHORIZED, NOT_BOUND, CAPABILITY_UNAVAILABLE,
    SYSTEM_ALREADY_BOUND, SYSTEM_UNAVAILABLE, PLAYER_OFFLINE, BUSY, CANCELLED, EXPIRED,
    BUDGET_EXCEEDED, ITERATION_LIMIT, RATE_LIMITED, RESULT_TOO_LARGE, MODULE_FAILED, TOOL_FAILED,
    EFFECT_UNKNOWN, CONFIRM_REQUIRED, HISTORY_HIDDEN, INVALID_SELECTION,

    AI_FORBIDDEN, FREE_CHAT_DISABLED, MODE_NOT_ALLOWED, MODEL_DISABLED, MODEL_NOT_CONFIGURED,
    MODEL_INVALID, MODEL_TIMEOUT, MODEL_UNAVAILABLE,

    SEARCH_INVALID_REQUEST, SEARCH_NOT_CONFIGURED, SEARCH_TIMEOUT, SEARCH_UNAVAILABLE,
    SEARCH_HTTP_ERROR, SEARCH_INVALID_RESPONSE,

    STORAGE_FULL, STORAGE_UNAVAILABLE, CATALOG_CHANGED, STALE_QUOTE, INVALID_COST,
    INSUFFICIENT_COST, INVALID_REWARD, INVENTORY_FULL, UNKNOWN_ITEM,
    EMPTY_HAND, ITEM_NOT_DAMAGED, JOB_ACTIVE, JOB_NOT_READY,
    UNKNOWN_CURRENCY, INSUFFICIENT_BALANCE, BALANCE_OVERFLOW,

    QUEST_UNAVAILABLE, QUEST_AMBIGUOUS, STALE_QUEST_LIST, STALE_REFERENCE,
    NO_SUBMITTABLE_TASK, CHOICE_REQUIRED, CLAIM_REJECTED;

    public static final FailureSource SOURCE = new FailureSource(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "agent"));

    @Override
    public FailureSource source() {
        return SOURCE;
    }

    @Override
    public String key() {
        // Keep the existing language keys while using Saturn's typed failure and message contract.
        return "system.error." + this.getSerializedName();
    }

    public static AgentFailure fromCode(String code) {
        try {
            return valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return MODULE_FAILED;
        }
    }
}
