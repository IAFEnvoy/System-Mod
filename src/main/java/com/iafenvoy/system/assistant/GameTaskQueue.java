package com.iafenvoy.system.assistant;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * The server tick drains a bounded amount; callbacks never wait for I/O.
 */
public final class GameTaskQueue {
    private final ArrayBlockingQueue<Runnable> pending = new ArrayBlockingQueue<>(128);

    public boolean offer(Runnable work) {
        return this.pending.offer(work);
    }

    public void tick() {
        int count = Math.min(16, this.pending.size());
        long end = System.nanoTime() + 2_000_000L;
        for (int i = 0; i < count && System.nanoTime() < end; i++) {
            Runnable work = this.pending.poll();
            if (work == null) break;
            work.run();
        }
    }
}
