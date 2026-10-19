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
    private Throwable failure;
    private final AtomicInteger nextIndex = new AtomicInteger();

    public RenderWorkers(int count) {
        workers = new Worker[count];
        for (int i = 0; i < count; i++) {
            workers[i] = new Worker("EngineRenderWorker-" + i, i);
            workers[i].start();
        }
    }
    public void execute(int start, int end, IntConsumer task) {
        Throwable error;
        boolean interrupted = false;
        synchronized (lock) {
            this.start = start;
            this.end = end;
            this.task = task;
            this.failure = null;
            nextIndex.set(start);
            remaining = workers.length;
            generation++;
            lock.notifyAll();
            while (remaining > 0 && !stopping) {
                try { lock.wait(); }
                catch (InterruptedException e) { interrupted = true; }
            }
            error = failure;
            failure = null;
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (error != null) throw new IllegalStateException("Render worker task failed", error);
    }
    public void shutdown() {
        synchronized (lock) {
            stopping = true;
            lock.notifyAll();
        }
    }
    private void recordFailure(Throwable t) {
        synchronized (lock) {
            if (failure == null) failure = t;
        }
    }
    private final class Worker extends Thread {
        private long seenGeneration;
        private final int index;
        Worker(String name, int index) { super(name); this.index = index; setDaemon(true); }
        @Override public void run() {
            while (true) {
                IntConsumer localTask;
                int localStart, localEnd;
                synchronized (lock) {
                    while (!stopping && seenGeneration == generation) {
                        try { lock.wait(); }
                        catch (InterruptedException e) { if (stopping) return; }
                    }
                    if (stopping) return;
                    seenGeneration = generation;
                    localEnd = end;
                    localTask = task;
                    localStart = Math.max(1, (end - start) / (workers.length * 8));
                }
                try {
                    for (int from = nextIndex.getAndAdd(localStart); from < localEnd; from = nextIndex.getAndAdd(localStart)) {
                        int to = Math.min(localEnd, from + localStart);
                        for (int i = from; i < to; i++) {
                            try { localTask.accept(i); }
                            catch (Throwable t) { recordFailure(t); }
                        }
                    }
                }
                finally {
                    synchronized (lock) { if (--remaining == 0) lock.notifyAll(); }
                }
            }
        }
    }
}