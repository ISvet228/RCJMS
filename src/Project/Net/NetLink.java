package Project.Net;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    boolean send(byte[] packet) { return !closed.get() && outbox.offer(packet); }
    void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
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
        } catch (IOException | RuntimeException ignored) {}
        finally { close(); }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                byte[] packet = outbox.take();
                out.write(packet);
                out.flush();
            }
        } catch (IOException | InterruptedException | RuntimeException ignored) { }
        finally { close(); }
    }
    //endregion
}