package com.iafenvoy.system.mixin;

import com.iafenvoy.system.screen.gui.AssistantPageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Treat assistant pages as whitelisted screens; skip this target when IMBlocker is absent.
 */
@Pseudo
@Mixin(targets = "io.github.reserveword.imblocker.common.IMBlockerConfig", remap = false)
public abstract class ImBlockerConfigMixin {
    @Inject(method = "isScreenInWhitelist", at = @At("HEAD"), cancellable = true)
    private void system$allowAssistantPages(Object screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof AssistantPageScreen) cir.setReturnValue(true);
    }
}
