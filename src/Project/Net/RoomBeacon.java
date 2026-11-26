package Project.Net;

import Project.Helpers.Log;
import java.io.*;
import java.net.*;

import static Project.Net.NetProtocol.*;

final class RoomBeacon {
    //region Variables
    private final NetSession session;
    private final DatagramSocket socket;
    //endregion

    //region Constructors
    private RoomBeacon(NetSession session, DatagramSocket socket) {
        this.session = session;
        this.socket = socket;
    }
    //endregion

    //region Lifecycle
    static RoomBeacon start(NetSession session) {
        try {
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(DISCOVERY_PORT));
            RoomBeacon beacon = new RoomBeacon(session, socket);
            Thread.ofPlatform().daemon(true).name("rcjms-net-beacon").start(beacon::serve);
            return beacon;
        } catch (IOException | RuntimeException exception) { return null; }
    }
    void close() {
        socket.close();
    }
    //endregion

    //region Serving
    private void serve() {
        byte[] buffer = new byte[DISCOVERY_REPLY_BYTES];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                answer(packet);
            } catch (IOException exception) { break; }
            catch (RuntimeException ignored) { Log.debug(ignored); }
        }
    }
    private void answer(DatagramPacket probe) throws IOException {
        if (!isProbe(probe)) return;
        byte[] reply = roomInfo();
        socket.send(new DatagramPacket(reply, reply.length, probe.getSocketAddress()));
    }
    private static boolean isProbe(DatagramPacket packet) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()))) {
            return in.readInt() == MAGIC && in.readByte() == DISCOVERY_PROBE;
        }
        catch (IOException exception) { return false; }
    }

    private byte[] roomInfo() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(DISCOVERY_ROOM);
            out.writeUTF(NetSettings.playerName());
            out.writeLong(session.roomId());
            out.writeInt(session.port());
            writeSpec(out, session.spec());
            out.writeInt(session.seats());
            out.writeInt(session.maxPlayers());
        }
        return bytes.toByteArray();
    }
    //endregion
}