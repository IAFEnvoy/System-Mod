package com.iafenvoy.system.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A consumed change flag shared safely by resource reloads and client/server ticks.
 */
public abstract class AbstractChangeTrackable implements ChangeTrackable {
    private final AtomicBoolean dirty = new AtomicBoolean();

    @Override
    public final void markDirty() {
        this.dirty.set(true);
    }

    @Override
    public boolean checkDirty() {
        return this.dirty.getAndSet(false);
    }
}
