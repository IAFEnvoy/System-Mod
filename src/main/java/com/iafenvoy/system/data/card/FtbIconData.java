package com.iafenvoy.system.data.card;

import com.iafenvoy.system.data.output.QuestIconOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Pure presentation data; no optional FTB class crosses the shared boundary.
 */
public record FtbIconData(String quest, @Nullable QuestIconOutput preview, Component title) {
    public static FtbIconData quest(String id, Component title) {
        return new FtbIconData(id, null, title);
    }

    public static FtbIconData type(QuestIconOutput preview) {
        return new FtbIconData("", preview, preview.title());
    }

    public String amount() {
        return this.preview == null ? "" : this.preview.amount();
    }
}
