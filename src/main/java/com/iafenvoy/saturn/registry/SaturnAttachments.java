package com.iafenvoy.saturn.registry;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.attachment.TriggerCooldownAttachment;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The entity attachments Saturn owns: the cooldown of a trigger rule.
 *
 * <p>Only state that belongs to a mechanism Saturn itself runs lives here. A module of a dependent mod keeps its own
 * attachments, so the reset one module performs cannot reach another's; a dependent that needs a store of its own
 * registers it with {@code NeoForgeRegistries.ATTACHMENT_TYPES} under its own namespace.
 */
public final class SaturnAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Saturn.MOD_ID);

    // Server-owned and never synced: nothing on the client draws a rule's cooldown.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TriggerCooldownAttachment>> TRIGGER_COOLDOWNS = entityServerOnly("trigger_cooldowns", TriggerCooldownAttachment::new, TriggerCooldownAttachment.CODEC);

    private SaturnAttachments() {
    }

    // Saved and copied on death but never sent to the client.
    private static <T> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entityServerOnly(String name, Supplier<T> factory, MapCodec<T> codec) {
        return REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec.codec()).copyOnDeath().build());
    }
}
