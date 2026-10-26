package com.iafenvoy.system.util;

public interface ChangeTrackable {
    void markDirty();

    boolean checkDirty();
}
