package com.iafenvoy.system.mixin;

import com.sighs.apricityui.client.CursorReleaseController;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * AUI's global hold key must not release the cursor when there is nothing to interact with.
 */
@Mixin(value = CursorReleaseController.class, remap = false)
public abstract class AuiCursorReleaseMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static boolean system$requireInteractiveDocument(boolean requested) {
        // Notifications use vanilla chat for interaction, never AUI's global cursor hold key.
        return requested && Document.getAll().stream().anyMatch(document -> document != null
                && document.isActive() && !document.isManuallyRendered()
                && !document.getPath().equals("system/assistant-message.html")
                && document.getElements().stream().anyMatch(element -> system$hasPointerTarget(document, element)));
    }

    private static boolean system$hasPointerTarget(Document document, Element element) {
        // Persistent toast roots can outlive their content; an empty document is not an input owner.
        if (element == document.documentElement || element == document.body || !element.isConnected()
                || !Interaction.isDisplayed(element) || !Interaction.isVisible(element)
                || !"auto".equals(element.getComputedStyle().pointerEvents)) return false;
        Size size = Size.box(element);
        return size.width() > 0 && size.height() > 0;
    }
}
