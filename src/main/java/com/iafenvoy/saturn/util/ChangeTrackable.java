package com.iafenvoy.saturn.util;

public interface ChangeTrackable {
    void markDirty();

    boolean checkDirty();
}
