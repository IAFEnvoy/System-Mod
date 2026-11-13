package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;

/** Only the requesting player's balances are included in this display snapshot. */
public record WalletOutput(List<Entry> currencies) implements CardData {
    public static final MapCodec<WalletOutput> CODEC = Entry.CODEC.listOf().fieldOf("currencies").xmap(WalletOutput::new, WalletOutput::currencies);

    public WalletOutput {
        currencies = List.copyOf(currencies);
    }

    @Override
    public MapCodec<WalletOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "wallet"))
                .title("system.ui.nav_economy").summary("system.result.balance");
        builder.list("currencies", this.currencies, (prefix, entry) -> builder.put(prefix + "name", entry.name)
                .put(prefix + "description", entry.description).put(prefix + "id", entry.id.toString())
                .put(prefix + "icon", ItemPreviewOutput.of(entry.icon)).put(prefix + "balance", Long.toString(entry.balance)));
        return builder.serverButton("refresh", "system.action.balance_refresh", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "balance"), Map.of());
    }

    public record Entry(Identifier id, Component name, Component description, ItemStackTemplate icon, long balance) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Entry::name),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Entry::description),
                ItemStackTemplate.CODEC.fieldOf("icon").forGetter(Entry::icon),
                Codec.LONG.fieldOf("balance").forGetter(Entry::balance)
        ).apply(i, Entry::new));
    }
}
