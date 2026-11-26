package Project.Net;

import Project.Helpers.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import Project.Helpers.MazeData;

import static Project.Net.NetProtocol.*;

public final class NetSession {
    //region Variables
    private static final int HOST_ID = 1;
    private static final int NO_ID = 0;
    private static final long MIGRATE_WAIT_NANOS = 8_000_000_000L;
    private static final long RETRY_MILLIS = 200;
    private static final int OPEN_ATTEMPTS = 10;
    private static final int BEACON_RETRY_TICKS = 10;
    private static final long FLUSH_MILLIS = 400;
    private static final long STALE_FORGET_MILLIS = 30_000;
    private static final long PENDING_MILLIS = 15_000;
    private static final double MAX_STEP_CELLS = 1.0;
    private static final double SMOOTH_SECONDS = 0.1, SNAP_CELLS = 2.5;
    private static final Map<String, Long> LAST_TOKENS = new ConcurrentHashMap<>();
    private static final Set<String> JOINED_ROOMS = ConcurrentHashMap.newKeySet();
    private static final Set<NetSession> OPEN = ConcurrentHashMap.newKeySet();
    private static volatile NetSession current;

    static { Runtime.getRuntime().addShutdownHook(new Thread(NetSession::closeAll, "rcjms-net-shutdown")); }

    private final MazeSpec spec;
    private final MazeData maze;
    private final boolean host;
    private final String room;
    private final int localId;
    private final long token;
    private final int port;
    private final ServerSocket server;
    private final int maxPlayers;
    private final long roomId;
    private volatile RoomBeacon beacon;
    private final Object lock = new Object();
    private final List<NetLink> links = new CopyOnWriteArrayList<>();
    private final Map<Integer, Remote> remotes = new ConcurrentHashMap<>();
    private final Map<Integer, double[]> shown = new HashMap<>();
    private volatile List<Member> pending = List.of();
    private volatile long pendingUntil;
    private final ScheduledExecutorService ticker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<Long, Integer> seats = new ConcurrentHashMap<>();
    private final Map<Integer, Long> tokens = new ConcurrentHashMap<>();
    private volatile List<Member> roster = List.of();
    private final Map<Integer, Integer> caughtSerial = new ConcurrentHashMap<>();
    private final Queue<Caught> news = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean localCaught = new AtomicBoolean();
    private final AtomicInteger runSerial = new AtomicInteger();
    private volatile List<Hunter> hunters = List.of();
    private int nextId = HOST_ID + 1;
    private int ticks;
    private volatile Pose pose = new Pose(Double.NaN, Double.NaN, 0, 0, Double.NaN);
    //endregion

    //region Records
    public record Joined(MazeSpec spec, MazeData maze) {}
    public record Hunter(int floor, double x, double y, int state, double stride, double alert, double lean, double duck, double arms, double scan, double clock) {}
    public record Caught(int id, int serial) {}
    private record Pose(double x, double y, int floor, double angle, double finishSeconds) {}
    private record Remote(double x, double y, int floor, double angle, double finishSeconds, int serial, long heardAt, double speed, double walked) {}
    private record Seat(int id, NetLink replaced) {}
    record Member(int id, long token, String address) {}
    //endregion

    //region Constructors
    private NetSession(MazeSpec spec, boolean host, String room, int localId, int hostId, int port, ServerSocket server, int maxPlayers, long roomId, long token, MazeData maze) {
        this.spec = spec;
        this.maze = maze;
        this.host = host;
        this.room = room;
        this.localId = localId;
        this.token = token;
        this.port = port;
        this.server = server;
        this.maxPlayers = maxPlayers;
        this.roomId = roomId;
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "rcjms-net-tick"));
    }
    //endregion

    //region Starting
    public static NetSession host(int port, MazeSpec spec) throws IOException { return host(port, spec, buildMaze(spec)); }
    public static NetSession host(int port, MazeSpec spec, MazeData maze) throws IOException { return open(port, spec, HOST_ID, 0, List.of(), maze, 0, null); }
    public static MazeData buildMaze(MazeSpec spec) {
        return MazeData.generate(spec.seed(), spec.width(), spec.height(), spec.floors(), spec.geometry(), spec.hasFloors(), spec.algorithm(), spec.finishMode());
    }
    public static Joined join(String host, int port) throws IOException {
        String room = host + ":" + port;
        boolean reserved = JOINED_ROOMS.add(room);
        long token = reserved ? LAST_TOKENS.computeIfAbsent(room, key -> ThreadLocalRandom.current().nextLong()) : ThreadLocalRandom.current().nextLong();
        boolean joined = false;
        try {
            NetSession session = connect(host, port, token, reserved ? room : null, null);
            joined = true;
            return new Joined(session.spec, session.maze);
        } finally { if (reserved && !joined) JOINED_ROOMS.remove(room); }
    }
    private static NetSession connect(String address, int port, long token, String room, NetSession previous) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(token);
            out.flush();
            if (in.readInt() != MAGIC) throw new IOException("not an RCJMS host");
            if (in.readInt() != VERSION) throw new IOException("version mismatch");
            byte status = in.readByte();
            if (status == REJECT_FULL) throw new IOException("the game is full");
            if (status != ACCEPT) throw new IOException("the host refused the join");
            int id = in.readInt();
            int hostId = in.readInt();
            long roomId = in.readLong();
            MazeSpec spec = readSpec(in);
            MazeData maze = readMaze(in, spec);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            NetSession session = new NetSession(spec, false, room, id, hostId, port, null, MAX_PLAYERS, roomId, token, maze);
            NetLink link = new NetLink(session, socket, in, out, hostId);
            session.links.add(link);
            if (previous != null) session.inherit(previous);
            session.startThreads(link::start);
            register(session);
            return session;
        } catch (RuntimeException exception) {
            closeQuietly(socket);
            throw new IOException(exception);
        } catch (IOException exception) {
            closeQuietly(socket);
            throw exception;
        }
    }
    private static NetSession open(int port, MazeSpec spec, int localId, long token, List<Member> roster, MazeData maze, long keepRoomId, NetSession previous) throws IOException {
        ServerSocket server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
        } catch (IOException exception) {
            closeQuietly(server);
            throw exception;
        }
        long roomId = keepRoomId != 0 ? keepRoomId : ThreadLocalRandom.current().nextLong();
        NetSession session = new NetSession(spec, true, null, localId, localId, server.getLocalPort(), server, NetSettings.maxPlayers(), roomId, token, maze);
        session.adopt(roster);
        if (previous != null) session.inherit(previous);
        session.startThreads(() -> daemon(session::acceptLoop, "rcjms-net-accept").start());
        session.beacon = RoomBeacon.start(session);
        register(session);
        return session;
    }
    public static NetSession current() { return current; }
    private static void register(NetSession session) {
        current = session;
        NetSettings.resetShowPlayers();
    }
    private Remote track(int sender, float x, float y, int floor, float angle, double finish, int serial) {
        long now = System.currentTimeMillis();
        Remote before = remotes.get(sender);
        if (before == null) return new Remote(x, y, floor, angle, finish, serial, now, 0, 0);
        double dist = Math.hypot(x - before.x(), y - before.y());
        boolean walk = floor == before.floor() && dist <= MAX_STEP_CELLS;
        if (!walk) return new Remote(x, y, floor, angle, finish, serial, now, before.speed() * 0.5, before.walked());
        double dt = Math.max(0.02, (now - before.heardAt()) / 1000.0);
        double speed = before.speed() * 0.6 + (dist / dt) * 0.4;
        return new Remote(x, y, floor, angle, finish, serial, now, speed, before.walked() + dist);
    }
    private void adopt(List<Member> members) {
        List<Member> waiting = new ArrayList<>();
        for (Member member : members) {
            if (member.token() != 0) {
                seats.put(member.token(), member.id());
                tokens.put(member.id(), member.token());
                if (member.id() != localId) waiting.add(member);
            }
            nextId = Math.max(nextId, member.id() + 1);
        }
        pending = List.copyOf(waiting);
        pendingUntil = System.currentTimeMillis() + PENDING_MILLIS;
    }
    private void inherit(NetSession previous) {
        runSerial.set(previous.runSerial.get());
        caughtSerial.putAll(previous.caughtSerial);
        remotes.putAll(previous.remotes);
        remotes.remove(localId);
        synchronized (previous.shown) { synchronized (shown) { shown.putAll(previous.shown); } }
        pose = previous.pose;
        if (previous.localCaught.get()) localCaught.set(true);
        news.addAll(previous.news);
    }
    private void startThreads(Runnable openSockets) {
        OPEN.add(this);
        openSockets.run();
        ticker.scheduleAtFixedRate(this::tick, 0, SEND_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }
    //endregion

    //region Public API
    public MazeSpec spec() { return spec; }
    public MazeData maze() { return maze; }
    public boolean isHost() { return host; }
    public int port() { return port; }
    public int localId() { return localId; }
    public boolean isClosed() { return closed.get(); }
    public int playerCount() { return 1 + rivals().size(); }
    int seats() { return 1 + links.size() + reservedSeats(); }
    private int reservedSeats() {
        if (System.currentTimeMillis() >= pendingUntil) return 0;
        int waiting = 0;
        for (Member member : pending) if (linkOf(member.id()) == null) waiting++;
        return waiting;
    }
    int maxPlayers() { return maxPlayers; }
    long roomId() { return roomId; }

    public List<Rival> rivals() {
        long now = System.currentTimeMillis();
        List<Rival> list = new ArrayList<>();
        synchronized (shown) {
            shown.keySet().retainAll(remotes.keySet());
            for (Map.Entry<Integer, Remote> entry : remotes.entrySet()) {
                Remote remote = entry.getValue();
                if (now - remote.heardAt() > STALE_MILLIS) continue;
                double[] at = smoothed(entry.getKey(), remote, now);
                list.add(new Rival(entry.getKey(), at[0], at[1], remote.floor(), at[2], remote.finishSeconds(), remote.speed(), remote.walked()));
            }
        }
        list.sort(Comparator.comparingInt(Rival::id));
        return list;
    }
    private double[] smoothed(int id, Remote remote, long now) {
        if (!Double.isFinite(remote.x()) || !Double.isFinite(remote.y())) {
            shown.remove(id);
            return new double[] { remote.x(), remote.y(), remote.angle(), remote.floor(), now };
        }
        double[] at = shown.get(id);
        if (at == null || !Double.isFinite(at[0]) || !Double.isFinite(at[1]) || at[3] != remote.floor() || Math.hypot(remote.x() - at[0], remote.y() - at[1]) > SNAP_CELLS) {
            at = new double[] { remote.x(), remote.y(), remote.angle(), remote.floor(), now };
        } else {
            double dt = Math.min(0.25, Math.max(0, now - at[4]) / 1000.0);
            double k = 1 - Math.exp(-dt / SMOOTH_SECONDS);
            at[0] += (remote.x() - at[0]) * k;
            at[1] += (remote.y() - at[1]) * k;
            at[2] += Math.IEEEremainder(remote.angle() - at[2], 2 * Math.PI) * k;
            at[4] = now;
        }
        shown.put(id, at);
        return at;
    }
    public void publish(double x, double y, int floor, double angle, double finishSeconds) { pose = new Pose(x, y, floor, angle, finishSeconds); }
    public void newRun() {
        runSerial.incrementAndGet();
        if (!host) hunters = List.of();
    }
    public boolean isCaught(int id) {
        Integer caughtAt = caughtSerial.get(id);
        if (caughtAt == null) return false;
        return caughtAt == currentSerial(id);
    }
    public void caught(int id) {
        if (!host || closed.get()) return;
        int serial = currentSerial(id);
        if (serial < 0 || isCaught(id)) return;
        markCaught(id, serial);
        byte[] packet = caughtPacket(id, serial);
        for (NetLink link : links) link.send(packet);
    }
    public boolean consumeLocalCaught() { return localCaught.getAndSet(false); }
    public List<Caught> takeCaught() {
        List<Caught> list = new ArrayList<>();
        Caught event;
        while ((event = news.poll()) != null) list.add(event);
        return list;
    }
    public void publishHunters(List<Hunter> list) { if (host) hunters = List.copyOf(list); }
    public List<Hunter> hunters() { return hunters; }
    public void leaveGracefully() {
        if (!closed.get()) {
            Pose last = pose;
            byte[] packet = state(localId, (float) last.x(), (float) last.y(), last.floor(), (float) last.angle(), last.finishSeconds(), runSerial.get());
            for (NetLink link : links) link.send(packet);
            for (NetLink link : links) link.awaitFlushed(FLUSH_MILLIS);
        }
        close();
    }
    public void close() {
        List<NetLink> toClose;
        synchronized (lock) {
            if (!closed.compareAndSet(false, true)) return;
            toClose = new ArrayList<>(links);
        }
        OPEN.remove(this);
        if (room != null) JOINED_ROOMS.remove(room);
        if (current == this) current = null;
        ticker.shutdownNow();
        closeQuietly(server);
        RoomBeacon room = beacon;
        if (room != null) room.close();
        for (NetLink link : toClose) link.close();
        links.clear();
        remotes.clear();
        hunters = List.of();
    }
    private static void closeAll() { for (NetSession session : OPEN) session.close(); }
    private int currentSerial(int id) {
        if (id == localId) return runSerial.get();
        Remote remote = remotes.get(id);
        return remote == null ? -1 : remote.serial();
    }
    private void markCaught(int id, int serial) {
        caughtSerial.put(id, serial);
        if (id == localId) localCaught.set(true);
        news.add(new Caught(id, serial));
    }
    //endregion

    //region Migration
    private void migrate() { daemon(this::runMigration, "rcjms-net-migrate").start(); }

    private void runMigration() {
        try {
            NetSession next = takeOver();
            if (next != null && closed.get()) next.close();
        } finally { close(); }
    }
    private NetSession takeOver() {
        List<Member> last = roster;
        List<Member> order = new ArrayList<>();
        for (int i = 1; i < last.size(); i++)
            if (last.get(i).id() != localId) order.add(last.get(i));
        order.add(new Member(localId, token, ""));
        order.sort(Comparator.comparingInt(Member::id));
        for (Member candidate : order) {
            if (closed.get()) return null;
            if (candidate.id() == localId) {
                NetSession opened = openOwnRoom(last);
                if (opened != null) return opened;
                continue;
            }
            NetSession joined = reach(candidate);
            if (joined != null) return joined;
        }
        return null;
    }
    private NetSession openOwnRoom(List<Member> last) {
        for (int attempt = 0; attempt < OPEN_ATTEMPTS && !closed.get(); attempt++) {
            try { return open(port, spec, localId, token, last, maze, roomId, this); }
            catch (IOException | RuntimeException exception) { sleepQuietly(RETRY_MILLIS); }
        }
        return null;
    }
    private NetSession reach(Member candidate) {
        long deadline = System.nanoTime() + MIGRATE_WAIT_NANOS;
        while (!closed.get() && System.nanoTime() < deadline) {
            try { return connect(candidate.address(), port, token, null, this); }
            catch (IOException exception) { sleepQuietly(RETRY_MILLIS); }
        }
        return null;
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
    //endregion

    //region Host Side
    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket socket = server.accept();
                daemon(() -> handshake(socket), "rcjms-net-hello").start();
            }
            catch (IOException exception) { break; }
        }
    }

    private void handshake(Socket socket) {
        try {
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                closeQuietly(socket);
                return;
            }
            long token = in.readLong();
            Seat seat = takeSeat(token);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            if (seat == null) {
                out.writeByte(REJECT_FULL);
                out.flush();
                closeQuietly(socket);
                return;
            }
            out.writeByte(ACCEPT);
            out.writeInt(seat.id());
            out.writeInt(localId);
            out.writeLong(roomId);
            writeSpec(out, spec);
            writeMaze(out, maze);
            out.flush();
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            NetLink link = new NetLink(this, socket, in, out, seat.id());
            if (!addLink(link, seat.replaced())) { link.close(); return; }
            if (seat.replaced() != null) seat.replaced().close();
            link.start();
            broadcastRoster();
            for (Map.Entry<Integer, Integer> entry : caughtSerial.entrySet())
                if (isCaught(entry.getKey())) link.send(caughtPacket(entry.getKey(), entry.getValue()));
        } catch (IOException | RuntimeException exception) { closeQuietly(socket); }
    }

    private Seat takeSeat(long token) {
        synchronized (lock) {
            if (closed.get()) return null;
            Integer known = seats.get(token);
            NetLink replaced = known == null ? null : linkOf(known);
            if (replaced == null && links.size() + 1 + (known == null ? reservedSeats() : 0) >= maxPlayers) return null;
            if (known != null) return new Seat(known, replaced);
            int id = nextId++;
            seats.put(token, id);
            tokens.put(id, token);
            return new Seat(id, null);
        }
    }

    private NetLink linkOf(int id) {
        for (NetLink link : links)
            if (link.id() == id) return link;
        return null;
    }

    private boolean addLink(NetLink link, NetLink replaced) {
        synchronized (lock) {
            if (closed.get()) return false;
            if (replaced != null) {
                replaced.markReplaced();
                links.remove(replaced);
            }
            links.add(link);
            return true;
        }
    }

    private void relay(byte[] packet, NetLink except) {
        for (NetLink link : links)
            if (link != except) link.send(packet);
    }
    private List<Member> members() {
        List<Member> list = new ArrayList<>();
        list.add(new Member(localId, token, ""));
        for (NetLink link : links) list.add(new Member(link.id(), tokens.getOrDefault(link.id(), 0L), link.address()));
        if (System.currentTimeMillis() < pendingUntil) {
            for (Member member : pending) {
                boolean connected = list.stream().anyMatch(known -> known.id() == member.id());
                if (!connected) list.add(member);
            }
        }
        return list;
    }
    private void broadcastRoster() {
        byte[] packet = rosterPacket(members());
        for (NetLink link : links) link.send(packet);
    }
    //endregion

    //region Messages
    void received(NetLink from, int type, DataInputStream in) throws IOException {
        if (type == TYPE_LEAVE && !host) {
            forget(in.readInt());
            return;
        }
        if (type == TYPE_CAUGHT && !host) {
            int id = in.readInt();
            int serial = in.readInt();
            markCaught(id, serial);
            return;
        }
        if (type == TYPE_HUNTERS && !host) {
            readHunters(in);
            return;
        }
        if (type == TYPE_ROSTER && !host) {
            roster = readRoster(in);
            return;
        }
        if (type != TYPE_STATE) throw new IOException("unknown packet " + type);
        int id = in.readInt();
        float x = in.readFloat(), y = in.readFloat();
        int floor = in.readInt();
        float angle = in.readFloat();
        double finish = in.readDouble();
        int serial = in.readInt();
        int sender = host ? from.id() : id;
        if (sender == localId) return;
        remotes.put(sender, track(sender, x, y, floor, angle, finish, serial));
        if (host) relay(state(sender, x, y, floor, angle, finish, serial), from);
    }

    private static List<Member> readRoster(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 1 || count > MAX_PLAYERS + 1) throw new IOException("bad roster size");
        List<Member> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = in.readInt();
            long token = in.readLong();
            String address = in.readUTF();
            list.add(new Member(id, token, address));
        }
        return List.copyOf(list);
    }

    private void readHunters(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_HUNTERS) throw new IOException("bad hunter count");
        List<Hunter> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int floor = in.readInt();
            double x = in.readFloat();
            double y = in.readFloat();
            int state = in.readUnsignedByte();
            double stride = in.readFloat();
            double alert = in.readFloat();
            double lean = in.readFloat();
            double duck = in.readFloat();
            double arms = in.readFloat();
            double scan = in.readFloat();
            double clock = in.readFloat();
            list.add(new Hunter(floor, x, y, state, stride, alert, lean, duck, arms, scan, clock));
        }
        hunters = List.copyOf(list);
    }
    private void forget(int id) {
        Remote remote = remotes.get(id);
        if (remote == null || Double.isNaN(remote.finishSeconds())) remotes.remove(id);
    }
    void linkClosed(NetLink link) {
        links.remove(link);
        if (closed.get() || link.isReplaced()) return;
        forget(link.id());
        if (host) {
            relay(leave(link.id()), null);
            broadcastRoster();
        }
        else migrate();
    }
    private void maintain() {
        long now = System.currentTimeMillis();
        remotes.values().removeIf(remote -> now - remote.heardAt() > STALE_FORGET_MILLIS);
        if (host && beacon == null && !closed.get()) {
            RoomBeacon started = RoomBeacon.start(this);
            if (started != null) {
                beacon = started;
                if (closed.get()) started.close();
            }
        }
    }
    private void tick() {
        try {
            if (host && !pending.isEmpty() && System.currentTimeMillis() >= pendingUntil) {
                pending = List.of();
                broadcastRoster();
            }
            if (++ticks % BEACON_RETRY_TICKS == 0) maintain();
            Pose now = pose;
            byte[] packet = state(localId, (float) now.x(), (float) now.y(), now.floor(), (float) now.angle(), now.finishSeconds(), runSerial.get());
            for (NetLink link : links) link.send(packet);
            if (host && spec.horror()) {
                byte[] monsters = huntersPacket(hunters);
                for (NetLink link : links) link.send(monsters);
            }
        } catch (RuntimeException ignored) { Log.debug(ignored); }
    }
    //endregion

    //region Threads
    private static Thread daemon(Runnable runnable, String name) { return Thread.ofPlatform().daemon(true).name(name).unstarted(runnable); }
    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) { Log.debug(ignored); }
    }
    //endregion
}