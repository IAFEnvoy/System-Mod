package com.iafenvoy.system.data.item;

import com.iafenvoy.assistant.action.AgentAction;

import java.util.Optional;

/**
 * A query may reference a bounded, server-owned snapshot of client search candidates.
 */
public interface ItemSearchQuery extends AgentAction {
    String query();

    int offset();

    /**
     * Item candidate cursors may differ from a catalog's own page offset.
     */
    default int searchOffset() {
        return this.offset();
    }

    Optional<String> searchRef();

    ItemSearchQuery withSearchRef(String reference);
}
