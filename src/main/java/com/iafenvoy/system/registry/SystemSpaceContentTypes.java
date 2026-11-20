package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.space.*;
import com.iafenvoy.system.data.space.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemSpaceContentTypes {
    public static final DeferredRegister<SpaceContentType<?>> REGISTRY = DeferredRegister.create(SystemResourceKeys.SPACE_CONTENT_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<SpaceContentType<?>, SpaceContentType<ItemSpaceContent>> ITEM = REGISTRY.register("item", ItemSpaceType::new);
    public static final DeferredHolder<SpaceContentType<?>, SpaceContentType<CurrencySpaceContent>> CURRENCY = REGISTRY.register("currency", CurrencySpaceType::new);
    public static final DeferredHolder<SpaceContentType<?>, SpaceContentType<ExperienceSpaceContent>> EXPERIENCE = REGISTRY.register("experience", ExperienceSpaceType::new);
    public static final DeferredHolder<SpaceContentType<?>, SpaceContentType<ActionSpaceContent>> ACTION = REGISTRY.register("action", ActionSpaceType::new);
    public static final DeferredHolder<SpaceContentType<?>, SpaceContentType<ChoiceSpaceContent>> CHOICE = REGISTRY.register("choice", ChoiceSpaceType::new);
}
