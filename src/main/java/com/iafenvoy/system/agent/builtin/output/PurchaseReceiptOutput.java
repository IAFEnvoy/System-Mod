package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardFact;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record PurchaseReceiptOutput(String orderId, String entitlementId, Reward reward, String state,
                                    Optional<Identifier> sourcePool) implements CardData {
    public static final MapCodec<PurchaseReceiptOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("orderId").forGetter(PurchaseReceiptOutput::orderId),
            Codec.STRING.fieldOf("entitlementId").forGetter(PurchaseReceiptOutput::entitlementId),
            Reward.CODEC.fieldOf("reward").forGetter(PurchaseReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(PurchaseReceiptOutput::state),
            Identifier.CODEC.optionalFieldOf("sourcePool").forGetter(PurchaseReceiptOutput::sourcePool)
    ).apply(i, PurchaseReceiptOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "purchase");

    public PurchaseReceiptOutput(String orderId, String entitlementId, Reward reward, String state) {
        this(orderId, entitlementId, reward, state, Optional.empty());
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.reward")
                .summary(this.sourcePool.isPresent() ? Component.translatable("system.tts.message.lottery_drawn", this.reward.label())
                        : Component.translatable("system.result.shop_purchased"));
        builder.reward("", this.reward())
                .put("order_id", this.orderId()).put("entitlement_id", this.entitlementId()).put("state", this.state())
                .enable("delivered", "DELIVERED".equals(this.state()));
        if (this.state.equals("COMPLETED"))
            builder.serverButton("deliver", "system.action.deliver", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "deliver"),
                    // The delivery action rebuilds this same card, so it carries what the rebuild cannot look up.
                    Map.of("entitlement", this.entitlementId(), "kind", "purchase", "order", this.orderId(),
                            "pool", this.sourcePool.map(Identifier::toString).orElse("")));
        this.sourcePool.ifPresent(id -> builder.serverButton("again", "system.action.lottery_again", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"), Map.of("pool", id.toString())));
        return builder;
    }

    @Override
    public MapCodec<PurchaseReceiptOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(OutputFacts.reward(this.entitlementId(), this.state(), this.reward()));
    }

}
