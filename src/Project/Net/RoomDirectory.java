package Project.Net;

import Project.Helpers.Log;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;

import static Project.Net.NetProtocol.*;

public final class RoomDirectory {
    //region Variables
    private static final long MIN_MILLIS = 50, MAX_MILLIS = 5000;
    //endregion

    public record Room(String name, String host, int port, MazeSpec spec, int players, int maxPlayers, long id) {}

    //region Constructors
    private RoomDirectory() {}
    //endregion

    //region Search
    public static List<Room> search(Duration timeout) {
        try { return probe(timeout); }
        catch (IOException | RuntimeException exception) { return List.of(); }
    }

    private static List<Room> probe(Duration timeout) throws IOException {
        long millis = Math.clamp(timeout.toMillis(), MIN_MILLIS, MAX_MILLIS);
        Map<Long, Room> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            sendProbes(socket);
            long until = System.nanoTime() + millis * 1_000_000L;
            while (true) {
                long left = (until - System.nanoTime()) / 1_000_000L;
                if (left <= 0) break;
                socket.setSoTimeout((int) left);
                DatagramPacket packet = new DatagramPacket(new byte[DISCOVERY_REPLY_BYTES], DISCOVERY_REPLY_BYTES);
                try { socket.receive(packet); }
                catch (SocketTimeoutException timedOut) { break; }
                readRoom(packet).ifPresent(room -> found.merge(room.id(), room, RoomDirectory::preferred));
            }
        }
        List<Room> rooms = new ArrayList<>(found.values());
        rooms.sort(Comparator.comparing(Room::name).thenComparing(Room::host).thenComparingInt(Room::port));
        return rooms;
    }
    private static Room preferred(Room current, Room candidate) { return isLoopback(current.host()) && !isLoopback(candidate.host()) ? candidate : current; }
    private static boolean isLoopback(String host) { return host.startsWith("127.") || host.equals("0:0:0:0:0:0:0:1") || host.equals("::1"); }
    private static void sendProbes(DatagramSocket socket) {
        byte[] probe = probeBytes();
        for (InetAddress target : targets()) {
            try { socket.send(new DatagramPacket(probe, probe.length, target, DISCOVERY_PORT)); }
            catch (IOException ignored) { Log.debug(ignored); }
        }
    }
    private static Set<InetAddress> targets() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
            targets.add(InetAddress.getLoopbackAddress());
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp()) continue;
                for (InterfaceAddress address : network.getInterfaceAddresses())
                    if (address.getBroadcast() != null) targets.add(address.getBroadcast());
            }
        } catch (IOException | RuntimeException ignored) { Log.debug(ignored); }
        return targets;
    }
    private static byte[] probeBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(5);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(DISCOVERY_PROBE);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }

    private static Optional<Room> readRoom(DatagramPacket packet) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()))) {
            if (in.readInt() != MAGIC || in.readByte() != DISCOVERY_ROOM) return Optional.empty();
            String name = in.readUTF();
            long id = in.readLong();
            int port = in.readInt();
            MazeSpec spec = readSpec(in);
            int players = in.readInt(), maxPlayers = in.readInt();
            if (port < 1 || port > 65535 || players < 1 || maxPlayers < players) return Optional.empty();
            String host = packet.getAddress().getHostAddress();
            return Optional.of(new Room(name, host, port, spec, players, maxPlayers, id));
        } catch (IOException | RuntimeException exception) { return Optional.empty(); }
    }
    //endregion
}