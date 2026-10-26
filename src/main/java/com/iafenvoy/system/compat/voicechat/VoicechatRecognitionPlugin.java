package com.iafenvoy.system.compat.voicechat;

import com.iafenvoy.system.SystemMod;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * SVC discovers this optional plugin; assistant recording must not also be sent to nearby players.
 */
@ForgeVoicechatPlugin
public final class VoicechatRecognitionPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "system-recognition";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (!FMLEnvironment.getDist().isClient()) return;
        try {
            Class.forName("com.iafenvoy.system.compat.voicechat.VoicechatRecognitionCapture")
                    .getMethod("registerEvents", EventRegistration.class).invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError error) {
            SystemMod.LOGGER.warn("Unable to install optional voice chat recognition adapter ({})", error.getClass().getSimpleName());
        }
    }
}
