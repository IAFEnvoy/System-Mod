package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.saturn.data.cost.builtin.ItemCost;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.IngredientEntry;
import com.iafenvoy.saturn.util.matcher.builtin.ItemEntry;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.assistant.progression.PlayerProgression;
import com.iafenvoy.system.data.catalog.*;
import com.iafenvoy.system.data.cost.CurrencyCost;
import com.iafenvoy.system.data.output.ShopPurchaseLimitOutput;
import com.iafenvoy.system.data.progression.ProgressionEstimate;
import com.iafenvoy.system.data.progression.ProgressionSettings;
import com.iafenvoy.system.data.space.CurrencySpaceContent;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * On-demand stack offers. Only bounded selectors survive a request; values and permissions are always rechecked.
 */
public final class ValueShopContribution implements ShopContribution {
    private final Identifier owner;
    private final ValueCommerceSettings settings;
    private final ShopTrade trade;
    private final double ceilingMultiplier;
    private final String prefix;
    private final ItemSearches searches;

    public ValueShopContribution(Identifier owner, ValueCommerceSettings settings, ShopTrade trade, double ceilingMultiplier, ItemSearches searches) {
        this.owner = owner;
        this.settings = settings;
        this.trade = trade;
        this.ceilingMultiplier = ceilingMultiplier;
        this.searches = searches;
        this.prefix = "generated/" + owner.getPath() + "/" + trade.getSerializedName() + "/";
    }

    @Override
    public Stream<? extends ShopOffer> offers(UUID player, CommerceCatalog catalog) {
        return Stream.empty();
    }

    @Override
    public boolean searchOnly(ShopTrade trade) {
        return this.trade == trade;
    }

    @Override
    public List<ShopOffer> requested(ServerPlayer player, CommerceCatalog fixed, ShopRequest request,
                                     Map<Identifier, ShopPurchaseLimitOutput> limits) {
        MinecraftServer server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Generated shop reads require the server thread");
        if (request.trade().filter(trade -> trade != this.trade).isPresent()) return List.of();
        if (request.query().isBlank() && request.ids().isEmpty()) return List.of();
        // Targeted resolution reads the player's server-owned selector, never client-supplied costs or rewards.
        List<ItemStackTemplate> targets = request.ids().stream().filter(id -> id.getNamespace().equals(this.owner.getNamespace())
                        && id.getPath().startsWith(this.prefix)).map(id -> this.searches.resolve(player.getUUID(), id))
                .flatMap(Optional::stream).distinct().toList();
        if (!request.ids().isEmpty() && targets.isEmpty()) return List.of();
        double ceiling = Double.MAX_VALUE;
        if (this.trade == ShopTrade.PURCHASE) {
            ProgressionEstimate progression = PlayerProgression.estimate(player, new ProgressionSettings(0.9,
                    Math.min(1, this.ceilingMultiplier), this.ceilingMultiplier, true));
            if (progression.value() <= 0) return List.of();
            ceiling = progression.upper();
        }
        List<ShopOffer> blockers = this.settings.hideFixed() ? fixed.offers().stream()
                .filter(offer -> offer instanceof ShopOffer.Fixed && offer.value().trade() == this.trade)
                .filter(offer -> CatalogAccess.usable(player, offer.value()))
                .filter(offer -> !limits.containsKey(offer.id()) || limits.get(offer.id()).available()).toList() : List.of();
        Predicate<ItemStack> blocked = blockers(blockers);
        List<ShopOffer> results = new ArrayList<>();
        // IDs are cheap to filter; only matching items are valued and turned into offers. Never collect the registry.
        Identifier exact = request.query().contains(":") ? Identifier.tryParse(request.query()) : null;
        Optional<Item> exactItem = exact == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(exact);
        Iterable<ItemStackTemplate> candidates = !request.ids().isEmpty() ? targets
                : request.candidates().isPresent() ? request.candidates().get().items()
                : exactItem.isPresent() ? List.of(new ItemStackTemplate(exactItem.get()))
                : () -> BuiltInRegistries.ITEM.stream().map(ItemStackTemplate::new).iterator();
        for (ItemStackTemplate template : candidates) {
            ItemStack stack = template.create();
            if (!ItemSearches.knownVariant(player, stack, this.trade)) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (request.candidates().isEmpty() && !request.query().isEmpty() && !id.toString().contains(request.query())
                    && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(request.query())) continue;
            if (this.settings.items().filter(entries -> !ItemMatcher.matches(entries, stack)).isPresent()
                    || ItemMatcher.matches(this.settings.excludedItems(), stack)) continue;
            if (blocked.test(stack)) continue;
            OptionalDouble value = ItemValues.get(server, stack);
            if (value.isEmpty() || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() <= 0 || value.getAsDouble() > ceiling)
                continue;
            double converted = value.getAsDouble() / this.settings.valuePerCurrency() * this.settings.priceMultiplier();
            double rounded = this.trade == ShopTrade.PURCHASE ? Math.ceil(converted) : Math.floor(converted);
            if (!Double.isFinite(rounded) || rounded < 1 || rounded > 9007199254740991D) continue;
            long price = (long) rounded;
            ShopOfferEntry entry = this.trade == ShopTrade.PURCHASE
                    ? new ShopOfferEntry(stack.getHoverName().copy(), List.of(template), List.of(),
                    List.of(new CurrencyCost(this.settings.currency(), new Constant(price))), Optional.empty(), true, EntryConditions.DEFAULT, this.trade)
                    : new ShopOfferEntry(stack.getHoverName().copy(), List.of(),
                    List.of(new SpaceStack(new CurrencySpaceContent(this.settings.currency()), price)),
                    // Exhaustive matching charges exactly the priced variant, including all its components.
                    List.of(new ItemCost(List.of(new IngredientEntry(DataComponentIngredient.of(true, stack))), new Constant(1))),
                    Optional.empty(), true, EntryConditions.DEFAULT, this.trade);
            Identifier offerId = this.searches.remember(player, this.prefix, this.owner.getNamespace(), template);
            String revision = offerId + "\0" + HolderHelper.id(this.settings.currency()) + "\0" + price + "\0" + value.getAsDouble();
            results.add(new ShopOffer.Generated(offerId, entry, revision, template));
            if (results.size() >= ShopRequest.MAX_RESULTS + 1) break;
        }
        return List.copyOf(results);
    }

    private static Predicate<ItemStack> blockers(List<ShopOffer> offers) {
        Set<Item> items = new HashSet<>();
        Set<ItemStackTemplate> stacks = new HashSet<>();
        Set<ItemMatcher.Entry> matchers = new LinkedHashSet<>();
        for (ShopOffer offer : offers) {
            if (offer.value().trade() == ShopTrade.BUYBACK) {
                offer.value().costs().stream().filter(ItemCost.class::isInstance).map(ItemCost.class::cast)
                        .flatMap(cost -> cost.entries().stream()).forEach(entry -> {
                            if (entry instanceof ItemEntry(Item item)) items.add(item);
                            else matchers.add(entry);
                        });
            } else {
                offer.value().items().forEach(item -> stacks.add(item.withCount(1)));
                offer.value().contents().forEach(content -> {
                    if (content.content() instanceof ItemSpaceContent(ItemStackTemplate item))
                        stacks.add(item.withCount(1));
                });
            }
        }
        List<ItemMatcher.Entry> entries = List.copyOf(matchers);
        return stack -> items.contains(stack.getItem()) || stacks.contains(ItemStackTemplate.fromNonEmptyStack(stack).withCount(1))
                || ItemMatcher.matches(entries, stack);
    }
}
