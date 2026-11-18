package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.data.module.SystemModule;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Objects;
import java.util.Optional;

/**
 * A trusted offer view; runtime sources need not insert generated entries into a datapack registry.
 */
public interface ShopOffer {
    Identifier id();

    ShopOfferEntry value();

    /**
     * A source must change this token whenever replacing an offer's contents or price.
     */
    String revision();

    default boolean searchOnly() {
        return false;
    }

    default Optional<ItemStackTemplate> stockItem() {
        return Optional.empty();
    }

    record Generated(Identifier id, ShopOfferEntry value, String revision,
                     ItemStackTemplate item) implements ShopOffer {
        @Override
        public boolean searchOnly() {
            return true;
        }

        @Override
        public Optional<ItemStackTemplate> stockItem() {
            return Optional.of(this.item);
        }
    }

    /**
     * Fixed entries retain their registry holder, including identity shared by multiple module selections.
     */
    record Fixed(Holder<ShopOfferEntry> holder, String revision) implements ShopOffer {
        public Fixed {
            Objects.requireNonNull(holder);
            Objects.requireNonNull(revision);
        }

        @Override
        public Identifier id() {
            return HolderHelper.id(this.holder);
        }

        @Override
        public ShopOfferEntry value() {
            return this.holder.value();
        }
    }

    /**
     * The aggregate supplies the real owner, so an identically named replacement cannot reuse an old quote.
     */
    record Sourced(Holder<SystemModule> definition, ShopOffer offer) implements ShopOffer {
        public Sourced {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(offer);
        }

        @Override
        public Identifier id() {
            return this.offer.id();
        }

        @Override
        public ShopOfferEntry value() {
            return this.offer.value();
        }

        @Override
        public String revision() {
            return HolderHelper.id(this.definition) + "\0" + this.offer.revision();
        }

        @Override
        public boolean searchOnly() {
            return this.offer.searchOnly();
        }

        @Override
        public Optional<ItemStackTemplate> stockItem() {
            return this.offer.stockItem();
        }
    }
}
