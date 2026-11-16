package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.data.space.CurrencySpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

public final class CurrencySpaceType implements SpaceContentType<CurrencySpaceContent> {
    @Override
    public MapCodec<CurrencySpaceContent> codec() {
        return CurrencySpaceContent.CODEC;
    }

    @Override
    public Component name(CurrencySpaceContent content) {
        return content.currency().value().name().copy();
    }

    @Override
    public IconReference icon(CurrencySpaceContent content) {
        return new IconReference(Either.right(content.currency().value().icon()));
    }

    @Override
    public String searchId(CurrencySpaceContent content) {
        return content.id().toString();
    }

    @Override
    public int batchSize(CurrencySpaceContent content) {
        return 64;
    }

    @Override
    public void prepare(CurrencySpaceContent content, long amount, SpaceTransfer transfer) {
        if (Wallets.find(transfer.player()).filter(wallets -> wallets.available(transfer.player())).isEmpty())
            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (transfer.player().registryAccess().lookupOrThrow(SystemResourceKeys.CURRENCY).getOptional(content.id()).isEmpty())
            throw new AgentException(AgentFailure.UNKNOWN_CURRENCY);
        transfer.currency(content.id(), amount);
    }
}
