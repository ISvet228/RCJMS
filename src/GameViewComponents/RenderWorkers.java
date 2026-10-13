package GameViewComponents;

import java.util.function.IntConsumer;
import java.util.concurrent.atomic.AtomicInteger;

public final class RenderWorkers {
    private final Object lock = new Object();
    private final Worker[] workers;
    private IntConsumer task;
    private int start, end, remaining;
    private long generation;
    private boolean stopping;
    private final AtomicInteger nextIndex = new AtomicInteger();

    public RenderWorkers(int count) {
        workers = new Worker[count];
        for (int i = 0; i < count; i++) {
            workers[i] = new Worker("GameViewRenderWorker-" + i, i);
            workers[i].start();
        }
    }
    public void execute(int start, int end, IntConsumer task) {
        synchronized (lock) {
            this.start = start;
            this.end = end;
            this.task = task;
            nextIndex.set(start);
            remaining = workers.length;
            generation++;
            lock.notifyAll();
            while (remaining > 0 && !stopping) {
                try { lock.wait(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    public void shutdown() {
        synchronized (lock) {
            stopping = true;
            lock.notifyAll();
        }
    }
    private final class Worker extends Thread {
        private long seenGeneration;
        private final int index;
        Worker(String name, int index) { super(name); this.index = index; setDaemon(true); }
        @Override public void run() {
            while (true) {
                IntConsumer localTask;
                int localStart, localEnd, index;
                synchronized (lock) {
                    while (!stopping && seenGeneration == generation) {
                        try { lock.wait(); }
                        catch (InterruptedException e) { if (stopping) return; }
                    }
                    if (stopping) return;
                    seenGeneration = generation;
                    localEnd = end;
                    localTask = task;
                    localStart = Math.max(1, (end - start) / (workers.length * 8)); //block size: small enough to balance uneven rows
                }
                for (int from = nextIndex.getAndAdd(localStart); from < localEnd; from = nextIndex.getAndAdd(localStart)) {
                    int to = Math.min(localEnd, from + localStart);
                    for (int i = from; i < to; i++) localTask.accept(i);
                }
                synchronized (lock) { if (--remaining == 0) lock.notifyAll(); }
            }
        }
    }
}