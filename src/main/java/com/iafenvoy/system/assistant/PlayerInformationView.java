package com.iafenvoy.system.assistant;

import com.iafenvoy.saturn.data.information.PlayerInformation;
import com.iafenvoy.saturn.data.information.PlayerViews;
import com.iafenvoy.saturn.data.information.model.InformationContext;
import com.iafenvoy.system.data.output.PlayerInformationOutput;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

/**
 * Captures all Saturn information on the server thread; the card node controls the display threshold.
 */
public final class PlayerInformationView {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerInformationView() {
    }

    /**
     * Saturn applies enabled/visible/missing policies and isolates individual row failures.
     * Keep translatable components intact until client rendering instead of resolving server-language strings.
     */
    public static List<PlayerInformationOutput> collect(ServerPlayer player) {
        try {
            InformationContext context = new InformationContext(player.clientInformation().language(), player.registryAccess());
            return PlayerInformation.collectAll(PlayerViews.parse(player), context).rows().stream()
                    .map(PlayerInformationOutput::of).toList();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.error("Failed to collect Saturn player information", error);
            return List.of();
        }
    }

}
