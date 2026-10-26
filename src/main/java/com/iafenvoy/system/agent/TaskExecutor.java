package com.iafenvoy.system.agent;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded scheduling and shutdown are shared by chat, durable events and store maintenance. */
final class TaskExecutor implements AutoCloseable {
    private final ExecutorService workers;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    TaskExecutor(String name, int workers, int queue) {
        this.workers = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue),
                Thread.ofPlatform().daemon().name(name + "-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    <T> CompletableFuture<T> submit(Callable<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        this.pending.add(result);
        result.whenComplete((value, error) -> this.pending.remove(result));
        if (this.closed.get()) {
            result.completeExceptionally(new AgentException("CANCELLED"));
            return result;
        }
        try {
            this.workers.execute(() -> {
                try {
                    if (result.isDone()) return;
                    result.complete(work.call());
                } catch (Exception error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(new AgentException(this.closed.get() ? "CANCELLED" : "BUSY"));
        }
        return result;
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.workers.shutdownNow();
        // Discarded queued work must still release its caller's ownership and complete its future.
        this.pending.forEach(result -> result.completeExceptionally(new AgentException("CANCELLED")));
        try {
            this.workers.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
