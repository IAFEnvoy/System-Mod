package com.iafenvoy.saturn.data.trigger;

/**
 * Keys for the payload values a trigger carries beyond the fixed context fields: the objects a formula cannot
 * hold - holders, dimension keys, entity collections. Numbers travel as formula values instead.
 */
public final class TriggerPayload {
    public static final String POTION = "saturn:potion";
    public static final String DIMENSION_FROM = "saturn:dimension_from";
    public static final String DIMENSION_TO = "saturn:dimension_to";
    public static final String PARENT = "saturn:parent";
    public static final String PARTNER = "saturn:partner";
    public static final String CHILD = "saturn:child";
    public static final String LIGHTNING = "saturn:lightning";
    public static final String BYSTANDERS = "saturn:bystanders";
    public static final String EFFECT_SOURCE = "saturn:effect_source";
    public static final String ITEMS = "saturn:items";
    // The place a polled trigger measures from (levitation start, fall start, lava entry, nether entry).
    public static final String START_POSITION = "saturn:start_position";
    public static final String BLOCK_STATE = "saturn:block_state";
    public static final String CHANGED_ITEM = "saturn:changed_item";
    // Vanilla's durability criterion reads the damage the stack carried when its own hook ran; a poll can only
    // recover that from the snapshot taken before the change.
    public static final String PREVIOUS_ITEM = "saturn:previous_item";
    public static final String CAUSE = "saturn:cause";
    public static final String THROWER = "saturn:thrower";

    private TriggerPayload() {
    }
}
