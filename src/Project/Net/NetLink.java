package Project.Net;

import Project.Helpers.Log;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class NetLink {
    //region Variables
    private static final int OUTBOX_CAPACITY = 64;
    private final NetSession owner;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final int id;
    private final BlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(OUTBOX_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger unwritten = new AtomicInteger();
    private volatile boolean replaced;
    //endregion

    //region Constructors
    NetLink(NetSession owner, Socket socket, DataInputStream in, DataOutputStream out, int id) {
        this.owner = owner;
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.id = id;
    }
    //endregion

    //region Lifecycle
    void start() {
        Thread.ofPlatform().daemon(true).name("rcjms-net-read-" + id).start(this::readLoop);
        Thread.ofPlatform().daemon(true).name("rcjms-net-write-" + id).start(this::writeLoop);
    }

    int id() { return id; }
    String address() { return socket.getInetAddress().getHostAddress(); }
    boolean isClosed() { return closed.get(); }
    void markReplaced() { replaced = true; }
    boolean isReplaced() { return replaced; }
    boolean send(byte[] packet) {
        if (closed.get()) return false;
        unwritten.incrementAndGet();
        if (outbox.offer(packet)) return true;
        unwritten.decrementAndGet();
        return false;
    }
    void awaitFlushed(long millis) {
        long until = System.nanoTime() + millis * 1_000_000L;
        while (unwritten.get() > 0 && !closed.get() && System.nanoTime() < until) {
            try { Thread.sleep(5); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
        }
    }
    void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { Log.debug(ignored); }
        owner.linkClosed(this);
    }
    //endregion

    //region Threads
    private void readLoop() {
        try {
            while (!closed.get()) {
                int type = in.readUnsignedByte();
                owner.received(this, type, in);
            }
        } catch (IOException | RuntimeException ignored) { Log.debug(ignored); }
        finally { close(); }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                byte[] packet = outbox.take();
                try {
                    out.write(packet);
                    out.flush();
                } finally { unwritten.decrementAndGet(); }
            }
        } catch (IOException | InterruptedException | RuntimeException ignored) { Log.debug(ignored); }
        finally { close(); }
    }
    //endregion
}