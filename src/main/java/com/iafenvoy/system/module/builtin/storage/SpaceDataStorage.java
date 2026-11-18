package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.space.*;
import com.iafenvoy.system.data.store.Delivery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;
import java.util.stream.Stream;

/**
 * General typed space contents and their optional reward provenance share one durable transaction.
 */
public final class SpaceDataStorage extends DataStorage {
    public static final MapCodec<SpaceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Delivery.CODEC).optionalFieldOf("deliveries", Map.of()).forGetter(SpaceDataStorage::getDeliveries),
            Codec.unboundedMap(Codec.STRING, SpaceEntry.CODEC).optionalFieldOf("entries", Map.of()).forGetter(SpaceDataStorage::getEntries),
            Codec.unboundedMap(Codec.STRING, ChoiceReceipt.CODEC).optionalFieldOf("choices", Map.of()).forGetter(SpaceDataStorage::choices),
            Codec.unboundedMap(Codec.STRING, SpaceDeposit.CODEC).optionalFieldOf("deposits", Map.of()).forGetter(SpaceDataStorage::deposits)
    ).apply(i, SpaceDataStorage::new));
    private final Map<String, Delivery> deliveries;
    private final Map<String, SpaceEntry> entries;
    private final Map<String, ChoiceReceipt> choices;
    private final Map<String, SpaceDeposit> deposits;

    public SpaceDataStorage() {
        this(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private SpaceDataStorage(Map<String, Delivery> deliveries, Map<String, SpaceEntry> entries,
                             Map<String, ChoiceReceipt> choices, Map<String, SpaceDeposit> deposits) {
        this.deliveries = new LinkedHashMap<>(deliveries);
        this.entries = new LinkedHashMap<>(entries);
        this.choices = new LinkedHashMap<>(choices);
        this.deposits = new LinkedHashMap<>(deposits);
    }

    public Map<String, SpaceEntry> getEntries() {
        return Collections.unmodifiableMap(this.entries);
    }

    /**
     * Credit arbitrary content under a new entry ID without creating a Reward.
     */
    public void deposit(String id, SpaceContent content, long amount) {
        if (id.isBlank() || amount <= 0) throw new AgentException(AgentFailure.INVALID_INPUT);
        if (this.entries.containsKey(id)) throw new AgentException(AgentFailure.INVALID_INPUT);
        this.addEntry(new SpaceEntry(id, content, amount, Optional.empty(), SpaceEntry.State.AVAILABLE));
    }

    private void addEntry(SpaceEntry entry) {
        this.assertEntryCount(capacity(entry.content(), entry.amount()), null);
        this.entries.put(entry.id(), entry);
        this.markDirty();
    }

    /**
     * Unpaid/uncertain orders retain their capacity until resolved, even before entries become visible.
     */
    private static <T extends SpaceContent> long capacity(T content, long amount) {
        return Math.clamp(SpaceContentType.of(content).capacity(content, amount), 1, 4097);
    }

    private static long capacity(Delivery delivery) {
        return delivery.reward.value().stacks().stream().mapToLong(stack -> capacity(stack.content(), stack.amount())).sum();
    }

    private void assertEntryCount(long additional, String replacing) {
        long reserved = this.deliveries.values().stream()
                .filter(delivery -> !Objects.equals(delivery.id, replacing) && !delivery.stored && delivery.reward != null)
                .filter(delivery -> Set.of("PAYMENT_PENDING", "PAYMENT_UNKNOWN", "PENDING").contains(delivery.state))
                .mapToLong(SpaceDataStorage::capacity).sum();
        reserved += this.deposits.values().stream().filter(SpaceDeposit::reserved).count();
        long occupied = this.entries.values().stream().mapToLong(entry -> capacity(entry.content(), entry.amount())).sum();
        if (occupied + reserved + additional > 4096) throw new AgentException(AgentFailure.STORAGE_FULL);
    }

    private void storeDelivery(Delivery delivery) {
        if (delivery.stored || !"PENDING".equals(delivery.state)) return;
        List<SpaceStack> stacks = delivery.reward.value().stacks();
        this.assertEntryCount(capacity(delivery), delivery.id);
        for (SpaceStack stack : stacks) {
            String id = UUID.randomUUID().toString();
            this.entries.put(id, new SpaceEntry(id, stack.content(), stack.amount(), Optional.of(delivery.id), SpaceEntry.State.AVAILABLE));
        }
        delivery.stored = true;
        if (stacks.isEmpty()) delivery.state = "DELIVERED";
        this.markDirty();
    }

    /**
     * Reserve all participating entries before any game-thread effects.
     */
    public void reserve(Map<String, Long> amounts) {
        for (Map.Entry<String, Long> request : amounts.entrySet()) {
            SpaceEntry entry = this.entries.get(request.getKey());
            if (entry == null || entry.state() != SpaceEntry.State.AVAILABLE || request.getValue() <= 0 || request.getValue() > entry.amount())
                throw new AgentException(AgentFailure.STALE_REFERENCE);
        }
        amounts.keySet().forEach(id -> this.entries.put(id, this.entries.get(id).withState(SpaceEntry.State.TAKING)));
        this.markDirty();
    }

    public void finish(Map<String, Long> amounts, boolean succeeded, boolean touched) {
        Set<String> origins = new HashSet<>();
        for (Map.Entry<String, Long> request : amounts.entrySet()) {
            SpaceEntry entry = this.entries.get(request.getKey());
            if (entry == null || entry.state() != SpaceEntry.State.TAKING)
                throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
            entry.entitlement().ifPresent(origins::add);
            long remaining = entry.amount() - (succeeded ? request.getValue() : 0);
            if (remaining == 0) this.entries.remove(entry.id());
            else
                this.entries.put(entry.id(), new SpaceEntry(entry.id(), entry.content(), remaining, entry.entitlement(),
                        !succeeded && touched ? SpaceEntry.State.UNKNOWN : SpaceEntry.State.AVAILABLE));
        }
        for (String origin : origins) {
            Delivery delivery = this.deliveries.get(origin);
            if (delivery == null) continue;
            boolean unknown = this.entries.values().stream().anyMatch(entry -> entry.entitlement().filter(origin::equals).isPresent() && entry.state() == SpaceEntry.State.UNKNOWN);
            boolean remaining = this.entries.values().stream().anyMatch(entry -> entry.entitlement().filter(origin::equals).isPresent());
            delivery.state = unknown ? "DELIVERY_UNKNOWN" : remaining ? "PENDING" : "DELIVERED";
        }
        this.markDirty();
    }

    public Map<String, Delivery> getDeliveries() {
        return this.deliveries;
    }

    public Delivery getDelivery(String id) {
        return this.deliveries.get(id);
    }

    public void putDelivery(String id, Delivery delivery) {
        this.assertDeliveryCount(id);
        if (!delivery.stored) this.assertEntryCount(capacity(delivery), id);
        this.deliveries.put(id, delivery);
        this.storeDelivery(delivery);
        this.markDirty();
    }

    public void putDeliveryIfAbsent(String id, Delivery delivery) {
        if (!this.deliveries.containsKey(id)) this.putDelivery(id, delivery);
    }

    public void removeDelivery(String id) {
        if (this.deliveries.remove(id) != null) {
            this.entries.values().removeIf(entry -> entry.entitlement().filter(id::equals).isPresent());
            this.markDirty();
        }
    }

    public void setDeliveryState(String id, String state) {
        Delivery delivery = this.deliveries.get(id);
        if (Objects.equals(delivery.state, state)) return;
        delivery.state = state;
        this.storeDelivery(delivery);
        this.markDirty();
    }

    public void recoverDeliveries() {
        boolean changed = false;
        for (Delivery delivery : this.deliveries.values())
            if ("DELIVERING".equals(delivery.state)) {
                delivery.state = "DELIVERY_UNKNOWN";
                changed = true;
            }
        for (Delivery delivery : this.deliveries.values()) this.storeDelivery(delivery);
        for (Map.Entry<String, SpaceEntry> entry : this.entries.entrySet())
            if (entry.getValue().state() == SpaceEntry.State.TAKING) {
                entry.setValue(entry.getValue().withState(SpaceEntry.State.UNKNOWN));
                entry.getValue().entitlement().map(this.deliveries::get).ifPresent(delivery -> delivery.state = "DELIVERY_UNKNOWN");
                changed = true;
            }
        if (changed) this.markDirty();
    }

    public Map<String, ChoiceReceipt> choices() {
        return Collections.unmodifiableMap(this.choices);
    }

    /** Selection and all selected outputs are committed in the same player-file transaction. */
    public ChoiceReceipt choose(String id, long ticket, int selected) {
        String key = ChoiceReceipt.key(id, ticket);
        ChoiceReceipt previous = this.choices.get(key);
        if (previous != null) return previous;
        SpaceEntry entry = this.entries.get(id);
        if (entry == null || entry.state() != SpaceEntry.State.AVAILABLE || entry.amount() != ticket
                || !(entry.content() instanceof ChoiceSpaceContent choice))
            throw new AgentException(AgentFailure.STALE_REFERENCE);
        if (selected < 0 || selected >= choice.options().size()) throw new AgentException(AgentFailure.INVALID_SELECTION);
        ChoiceReceipt receipt = new ChoiceReceipt(id, ticket, choice.name(), selected, choice.options().get(selected), System.currentTimeMillis());
        if (ticket == 1) this.entries.remove(id);
        else this.entries.put(id, new SpaceEntry(id, choice, ticket - 1, entry.entitlement(), entry.state()));
        choice.options().get(selected).stacks().forEach(stack -> this.addEntry(new SpaceEntry(UUID.randomUUID().toString(),
                stack.content(), stack.amount(), entry.entitlement(), SpaceEntry.State.AVAILABLE)));
        this.choices.put(key, receipt);
        this.markDirty();
        return receipt;
    }

    public Map<String, SpaceDeposit> deposits() {
        return Collections.unmodifiableMap(this.deposits);
    }

    public long depositUsage() {
        return this.entries.values().stream().filter(entry -> entry.entitlement().filter(id -> id.startsWith("deposit:")).isPresent()).count()
                + this.deposits.values().stream().filter(SpaceDeposit::reserved).count();
    }

    public void beginDeposit(SpaceDeposit deposit, int limit) {
        if (this.deposits.containsKey(deposit.id())) throw new AgentException(AgentFailure.STALE_REFERENCE);
        if (limit <= 0 || this.depositUsage() >= limit) throw new AgentException(AgentFailure.SPACE_DEPOSIT_FULL);
        this.assertEntryCount(1, null);
        List<SpaceDeposit> finished = this.deposits.values().stream().filter(value -> !value.reserved())
                .sorted(Comparator.comparingLong(SpaceDeposit::created).reversed()).toList();
        finished.stream().skip(127).forEach(value -> this.deposits.remove(value.id()));
        this.deposits.put(deposit.id(), deposit);
        this.markDirty();
    }

    public void executingDeposit(String id) {
        SpaceDeposit deposit = this.deposits.get(id);
        if (deposit == null || deposit.state() != SpaceDeposit.State.PREPARED) throw new AgentException(AgentFailure.STALE_REFERENCE);
        this.deposits.put(id, deposit.withState(SpaceDeposit.State.EXECUTING));
        this.markDirty();
    }

    public void finishDeposit(String id, boolean succeeded, boolean touched) {
        SpaceDeposit deposit = this.deposits.get(id);
        if (deposit == null) throw new AgentException(AgentFailure.STALE_REFERENCE);
        if (!deposit.reserved()) return;
        if (succeeded && this.entries.containsKey(deposit.entry())) throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
        this.deposits.put(id, deposit.withState(succeeded ? SpaceDeposit.State.COMPLETED
                : touched ? SpaceDeposit.State.UNKNOWN : SpaceDeposit.State.FAILED));
        if (succeeded) this.addEntry(new SpaceEntry(deposit.entry(), new ItemSpaceContent(deposit.item().withCount(1)),
                deposit.amount(), Optional.of(deposit.entry()), SpaceEntry.State.AVAILABLE));
        this.markDirty();
    }

    public void recoverDeposits(Set<String> active) {
        for (SpaceDeposit deposit : List.copyOf(this.deposits.values())) {
            if (active.contains(deposit.id())) continue;
            if (deposit.state() == SpaceDeposit.State.PREPARED) this.finishDeposit(deposit.id(), false, false);
            else if (deposit.state() == SpaceDeposit.State.EXECUTING) this.finishDeposit(deposit.id(), false, true);
        }
    }

    public SpaceDeposit reviewDeposit(String id, boolean consumed, String reviewer, String reason) {
        SpaceDeposit deposit = this.deposits.get(id);
        if (deposit == null || deposit.state() != SpaceDeposit.State.UNKNOWN) throw new AgentException(AgentFailure.STALE_REFERENCE);
        this.finishDeposit(id, consumed, false);
        SpaceDeposit finished = this.deposits.get(id);
        SpaceDeposit reviewed = new SpaceDeposit(id, finished.item(), finished.slot(), finished.amount(), finished.state(),
                finished.created(), reviewer, reason);
        this.deposits.put(id, reviewed);
        this.markDirty();
        return reviewed;
    }

    public Stream<Delivery> streamDeliveries() {
        return this.deliveries.values().stream();
    }

    public void assertDeliveryCount() {
        if (this.deliveries.size() >= 4096) throw new AgentException(AgentFailure.STORAGE_FULL);
    }

    public void assertDeliveryCount(String id) {
        if (!this.deliveries.containsKey(id)) this.assertDeliveryCount();
    }

    @Override
    public MapCodec<SpaceDataStorage> codec() {
        return CODEC;
    }

    @Override
    public SpaceDataStorage copy() {
        Map<String, Delivery> copy = new LinkedHashMap<>();
        this.deliveries.forEach((key, value) -> copy.put(key, value.copy()));
        return new SpaceDataStorage(copy, this.entries, this.choices, this.deposits);
    }
}
