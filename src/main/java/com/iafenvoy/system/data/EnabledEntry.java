package com.iafenvoy.system.data;

/**
 * A data-pack entry that can be switched off in place; the catalogue drops disabled entries before runtime use.
 */
public interface EnabledEntry {
    boolean enabled();
}
