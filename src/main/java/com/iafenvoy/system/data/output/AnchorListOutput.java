package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.store.TeleportAnchor;
import com.iafenvoy.system.util.CardEventValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A personal server snapshot; clients cannot supply destination coordinates.
 */
public record AnchorListOutput(List<TeleportAnchor> anchors, int limit, int cooldown,
                               Component feedback, List<PublicDestination> destinations, Optional<PaymentQuote> quote,
                               String quoteDestination) implements CardData {
    public static final MapCodec<AnchorListOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TeleportAnchor.CODEC.listOf().fieldOf("anchors").forGetter(AnchorListOutput::anchors),
            Codec.INT.fieldOf("limit").forGetter(AnchorListOutput::limit),
            Codec.INT.fieldOf("cooldown").forGetter(AnchorListOutput::cooldown),
            ComponentSerialization.CODEC.optionalFieldOf("feedback", Component.empty()).forGetter(AnchorListOutput::feedback),
            PublicDestination.CODEC.listOf().optionalFieldOf("destinations", List.of()).forGetter(AnchorListOutput::destinations),
            PaymentQuote.CODEC.optionalFieldOf("quote").forGetter(AnchorListOutput::quote),
            Codec.STRING.optionalFieldOf("quoteDestination", "").forGetter(AnchorListOutput::quoteDestination)
    ).apply(i, AnchorListOutput::new));

    public AnchorListOutput {
        anchors = List.copyOf(anchors);
        destinations = List.copyOf(destinations);
    }

    public record PublicDestination(TeleportAnchor anchor, Component label, Component description, boolean locked) {
        public static final Codec<PublicDestination> CODEC = RecordCodecBuilder.create(i -> i.group(
                TeleportAnchor.CODEC.fieldOf("anchor").forGetter(PublicDestination::anchor),
                ComponentSerialization.CODEC.fieldOf("label").forGetter(PublicDestination::label),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(PublicDestination::description),
                Codec.BOOL.fieldOf("locked").forGetter(PublicDestination::locked)
        ).apply(i, PublicDestination::new));
    }

    @Override
    public MapCodec<AnchorListOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_list"))
                .title("system.ui.nav_anchor").summary("system.result.anchor_list")
                .put("capacity", Component.translatable("system.anchor.capacity", this.anchors.size(), this.limit))
                .put("cooldown", Component.translatable("system.anchor.cooldown", this.cooldown))
                .enable("cooling", this.cooldown > 0).put("feedback", this.feedback)
                .enable("feedback", !this.feedback.equals(Component.empty()));
        Identifier teleport = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_teleport");
        Identifier delete = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_delete");
        Identifier rename = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_rename");
        builder.list("anchors", this.anchors, (prefix, anchor) -> {
            int index = this.anchors.indexOf(anchor);
            boolean locked = index >= this.limit;
            builder.put(prefix + "name", anchor.name()).put(prefix + "dimension", anchor.dimension().identifier().toString())
                    .put(prefix + "position", String.format(Locale.ROOT, "%.1f, %.1f, %.1f", anchor.position().x, anchor.position().y, anchor.position().z))
                    .enable(prefix + "locked", locked)
                    .put(prefix + "rename_binding", "actions." + (index * 3 + 1) + ".click")
                    .put(prefix + "teleport_value", CardEventValue.encode(teleport, Map.of("anchor", anchor.id())))
                    .put(prefix + "delete_value", CardEventValue.encode(delete, Map.of("anchor", anchor.id())));
            builder.serverButton("teleport." + anchor.id(), "system.action.anchor_teleport", teleport, Map.of("anchor", anchor.id()),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("disabled", locked)
                    .actionParameter("label", Component.translatable("system.anchor.teleport_named", anchor.name()));
            builder.button(new CardButton("rename." + anchor.id(), "system.action.anchor_rename",
                            new CardAction.Local(rename, Map.of("anchor", anchor.id(), "name", anchor.name())),
                            CardButton.Placement.BODY, CardButton.Behavior.WAIT))
                    .actionParameter("label", Component.translatable("system.anchor.rename_named", anchor.name()));
            builder.serverButton("delete." + anchor.id(), "system.action.anchor_delete", delete, Map.of("anchor", anchor.id()),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("label", Component.translatable("system.anchor.delete_named", anchor.name()));
        });
        builder.list("destinations", this.destinations, (prefix, destination) -> builder.put(prefix + "label", destination.label())
                .put(prefix + "description", destination.description()).put(prefix + "dimension", destination.anchor().dimension().identifier().toString())
                .enable(prefix + "locked", destination.locked())
                .put(prefix + "teleport_value", CardEventValue.encode(teleport, Map.of("anchor", destination.anchor().id()))));
        for (PublicDestination destination : this.destinations)
            builder.serverButton("public." + destination.anchor().id(), "system.action.anchor_teleport", teleport, Map.of("anchor", destination.anchor().id()),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("disabled", destination.locked()).actionParameter("label", destination.label());
        builder.enable("quote", this.quote.isPresent());
        this.quote.ifPresent(value -> {
            builder.put("quote_label", value.label()).price(value.price());
            builder.serverButton("confirm_travel", "system.anchor.confirm_travel", teleport,
                            Map.of("anchor", this.quoteDestination, "quote", value.reference()), CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("footer", true);
        });
        return builder.serverButton("save", "system.action.anchor_save", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_save"), Map.of())
                .actionParameter("disabled", this.anchors.size() >= this.limit).actionParameter("footer", true);
    }
}
