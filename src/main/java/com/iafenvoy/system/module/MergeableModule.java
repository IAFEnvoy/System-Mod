package com.iafenvoy.system.module;

import com.iafenvoy.system.module.merge.MergeType;

/**
 * Independent definitions opt into a typed group without losing their own identity or lifecycle.
 */
public interface MergeableModule<C> extends ServerModule {
    MergeType<C> mergeType();

    C contribution(ModuleContext context);
}
