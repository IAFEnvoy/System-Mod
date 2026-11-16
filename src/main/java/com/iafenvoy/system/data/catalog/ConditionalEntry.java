package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.data.EnabledEntry;

/**
 * Shared datapack fields; evaluation belongs to the server's catalog access service.
 */
public interface ConditionalEntry extends EnabledEntry {
    EntryConditions conditions();
}
