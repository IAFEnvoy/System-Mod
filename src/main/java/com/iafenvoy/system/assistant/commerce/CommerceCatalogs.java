package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ConditionalEntry;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.catalog.SignInRewardEntry;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.output.ShopPurchaseLimitOutput;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Server catalogue permissions; re-evaluated for every browse, quote and confirmation.
 */
public final class CommerceCatalogs {
    private final CommerceCatalog catalog;
    private final CatalogAccess access;
    private final BiPredicate<UUID, Holder<SystemModule>> enabled;
    private final Set<Holder<SystemModule>> modules = new LinkedHashSet<>();
    private ShopInventory shop;

    public CommerceCatalogs(@NotNull CommerceCatalog catalog, @NotNull BiPredicate<UUID, Holder<SystemModule>> enabled, CatalogAccess access) {
        this.catalog = catalog;
        this.enabled = enabled;
        this.access = access;
    }

    public void register(Holder<SystemModule> module) {
        if (!this.modules.add(module))
            throw new IllegalArgumentException("Duplicate module catalog " + HolderHelper.id(module));
    }

    public void shop(ShopInventory inventory) {
        if (this.shop != null) throw new IllegalStateException("Shop inventory already installed");
        this.shop = Objects.requireNonNull(inventory);
    }

    public CommerceCatalog all() {
        return this.catalog;
    }

    public CatalogAccess access() {
        return this.access;
    }

    /**
     * One game-thread pass supplies visible entries and evaluated availability, without exporting conditions.
     */
    public record View(CommerceCatalog catalog, Set<Identifier> lockedRewards, Set<Identifier> lockedOffers,
                       Set<Identifier> lockedLotteries) {
        public View {
            lockedRewards = Set.copyOf(lockedRewards);
            lockedOffers = Set.copyOf(lockedOffers);
            lockedLotteries = Set.copyOf(lockedLotteries);
        }
    }

    public View view(ExecutionContext context) {
        return this.access.read(context, this::view);
    }

    public View view(ServerPlayer player) {
        CommerceCatalog catalog = this.available(player.getUUID());
        Set<Identifier> rewards = new HashSet<>(), offers = new HashSet<>(), pools = new HashSet<>();
        return new View(new CommerceCatalog(catalog.revision(),
                visible(player, catalog.rewards(), rewards), catalog.offers().stream()
                .filter(offer -> CatalogAccess.visible(player, offer.value())).peek(offer -> {
                    if (!CatalogAccess.usable(player, offer.value())) offers.add(offer.id());
                }).toList(),
                visible(player, catalog.lotteries(), pools), catalog.registries()), rewards, offers, pools);
    }

    public boolean searchOnly(UUID player, ShopTrade trade) {
        return this.shop != null && this.shop.searchOnly(player, trade);
    }

    public View view(ServerPlayer player, ShopRequest request, Map<Identifier, ShopPurchaseLimitOutput> limits) {
        View fixed = this.view(player);
        if (this.shop == null) return fixed;
        CommerceCatalog catalog = fixed.catalog();
        Set<Identifier> locked = new HashSet<>(fixed.lockedOffers());
        return new View(new CommerceCatalog(catalog.revision(), catalog.rewards(),
                this.shop.requested(player, catalog, request, limits).stream()
                        .filter(offer -> CatalogAccess.visible(player, offer.value())).peek(offer -> {
                            if (!CatalogAccess.usable(player, offer.value())) locked.add(offer.id());
                        }).toList(), catalog.lotteries(), catalog.registries()),
                fixed.lockedRewards(), locked, fixed.lockedLotteries());
    }

    private static <T extends ConditionalEntry> List<Holder<T>> visible(ServerPlayer player, List<Holder<T>> entries, Set<Identifier> locked) {
        return entries.stream().filter(holder -> CatalogAccess.visible(player, holder.value())).peek(holder -> {
            if (!CatalogAccess.usable(player, holder.value())) locked.add(HolderHelper.id(holder));
        }).toList();
    }

    /**
     * Event rewards ignore the sign-in module selection, but still enforce the reward entry's own conditions.
     */
    public Holder<SignInRewardEntry> requireReward(ExecutionContext context, Identifier id, boolean event) {
        return this.access.read(context, player -> {
            Holder<SignInRewardEntry> reward = (event ? this.catalog : this.available(player.getUUID())).rewards().stream()
                    .filter(holder -> HolderHelper.id(holder).equals(id)).findFirst()
                    .orElseThrow(() -> new AgentException(AgentFailure.CATALOG_CHANGED));
            CatalogAccess.require(player, reward.value());
            return reward;
        });
    }

    public CommerceCatalog available(UUID player) {
        List<CatalogModule<?>> modules = this.modules.stream()
                .filter(holder -> this.enabled.test(player, holder))
                .flatMap(holder -> holder.value().server().stream()).filter(CatalogModule.class::isInstance)
                .<CatalogModule<?>>map(CatalogModule.class::cast).toList();
        return new CommerceCatalog(this.catalog.revision(),
                this.filter(this.catalog.rewards(), SystemResourceKeys.SIGNIN_REWARD, modules),
                this.shop == null ? List.of() : this.shop.offers(player, this.catalog),
                this.filter(this.catalog.lotteries(), SystemResourceKeys.LOTTERY_POOL, modules),
                this.catalog.registries());
    }

    private <T> List<Holder<T>> filter(List<Holder<T>> entries, ResourceKey<Registry<T>> registry, List<CatalogModule<?>> modules) {
        return entries.stream().filter(entry -> modules.stream().anyMatch(module ->
                module.catalog().equals(registry) && module.allows(this.catalog.registries(), HolderHelper.id(entry)))).toList();
    }
}
