package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.LotteryPrizeOutput;
import com.iafenvoy.system.screen.card.CardGrid;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shelf of prize samples mirroring the shop: one cell per prize with up to two previews and the prize label. Each
 * preview keeps the vanilla item tooltip and appends that prize's odds, so the detail view inspects one item.
 */
public record PrizeGridNode(String prizes, CardGrid grid) implements CardNode {
    private static final int MAX_CELL_ITEMS = 2;

    public static final MapCodec<PrizeGridNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("prizes", "{{prize_list}}").forGetter(PrizeGridNode::prizes),
            CardGrid.CODEC.forGetter(PrizeGridNode::grid)
    ).apply(i, PrizeGridNode::new));

    @Override
    public MapCodec<PrizeGridNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "offer_grid";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<?> source = view.context().get(CardRenderer.binding(this.prizes, view.indices()), List.class);
        if (source == null) return null;
        List<LotteryPrizeOutput> prizes = new ArrayList<>();
        for (Object value : source) if (value instanceof LotteryPrizeOutput prize) prizes.add(prize);
        return this.grid.render(view, "prizes:" + CardRenderer.binding(this.prizes, view.indices()), prizes, view.css(), (parent, prize) -> {
            Element cell = AssistantView.element(view.document(), "div", "offer_cell prize_cell", "");
            cell.setInlineStyleProperty("height", this.grid.cell() + "px");
            Element items = AssistantView.element(view.document(), "div", "offer_cell_items", "");
            List<Component> tooltip = tooltip(prize);
            for (ItemPreviewOutput preview : previews(prize).stream().limit(MAX_CELL_ITEMS).toList()) {
                Element slot = AssistantItems.entry(view.document(), preview, tooltip);
                if (slot != null) items.appendChild(slot);
            }
            if (!items.children.isEmpty()) cell.appendChild(items);
            cell.appendChild(AssistantView.element(view.document(), "div", "offer_cell_label", LegacyText.formatted(prize.reward().value().label())));
            parent.appendChild(cell);
        });
    }

    /**
     * The cell label already names the prize, so the tooltip only adds the odds to the vanilla item lines. The cost
     * is a separate line in the card body.
     */
    private static List<Component> tooltip(LotteryPrizeOutput prize) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("system.ui.lottery_chance", prize.chance() + "%").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static List<ItemPreviewOutput> previews(LotteryPrizeOutput prize) {
        return prize.reward().value().items().stream().map(ItemPreviewOutput::of).toList();
    }
}
