package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.LinkedHashMap;
import java.util.Map;

/** Whole units stay exact; removed definitions do not delete their saved balances. */
public final class WalletDataStorage extends DataStorage {
    public static final MapCodec<WalletDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, ExtraCodecs.NON_NEGATIVE_LONG).optionalFieldOf("balances", Map.of())
                    .forGetter(WalletDataStorage::getBalances)
    ).apply(i, WalletDataStorage::new));
    private final Map<Identifier, Long> balances;

    public WalletDataStorage() {
        this(Map.of());
    }

    private WalletDataStorage(Map<Identifier, Long> balances) {
        this.balances = new LinkedHashMap<>(balances);
    }

    public Map<Identifier, Long> getBalances() {
        return Map.copyOf(this.balances);
    }

    public long getBalance(Identifier currency) {
        return this.balances.getOrDefault(currency, 0L);
    }

    public long setBalance(Identifier currency, long amount) {
        if (amount < 0) throw new AgentException(AgentFailure.INSUFFICIENT_BALANCE);
        if (this.getBalance(currency) != amount) {
            this.balances.put(currency, amount);
            this.markDirty();
        }
        return amount;
    }

    public long addBalance(Identifier currency, long amount) {
        try {
            return this.setBalance(currency, Math.addExact(this.getBalance(currency), amount));
        } catch (ArithmeticException error) {
            throw new AgentException(AgentFailure.BALANCE_OVERFLOW, error);
        }
    }

    @Override
    public MapCodec<WalletDataStorage> codec() {
        return CODEC;
    }

    @Override
    public WalletDataStorage copy() {
        return new WalletDataStorage(this.balances);
    }
}
