package Project.Net;

import Project.Helpers.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import Project.Helpers.MazeGenerator;

final class NetProtocol {
    //region Constants
    static final int MAGIC = 0x52434A4D;
    static final int VERSION = 4;
    static final int MAX_PLAYERS = 8;
    static final int CONNECT_TIMEOUT_MILLIS = 3000;
    static final int HANDSHAKE_TIMEOUT_MILLIS = 3000;
    static final int READ_TIMEOUT_MILLIS = 5000;
    static final int SEND_INTERVAL_MILLIS = 100;
    static final long STALE_MILLIS = 3000;
    static final int MAX_SEED_BYTES = 1024;
    static final int MAX_ID_BYTES = 64;
    static final int MAX_SIZE = 1000;
    static final int MAX_FLOORS = 32;
    static final int MAX_HUNTERS = 4096;

    static final byte ACCEPT = 1;
    static final byte REJECT_FULL = 2;
    static final int DISCOVERY_PORT = Integer.getInteger("rcjms.net.discoveryPort", 7776);
    static final byte DISCOVERY_PROBE = 1;
    static final byte DISCOVERY_ROOM = 2;
    static final int DISCOVERY_REPLY_BYTES = 4096;

    static final byte TYPE_STATE = 1;
    static final byte TYPE_LEAVE = 2;
    static final byte TYPE_HUNTERS = 3;
    static final byte TYPE_CAUGHT = 4;
    static final byte TYPE_ROSTER = 5;
    //endregion

    //region Constructors
    private NetProtocol() {}
    //endregion

    //region Maze Spec
    static void writeSpec(DataOutputStream out, MazeSpec spec) throws IOException {
        byte[] seed = spec.seed().getBytes(StandardCharsets.UTF_8);
        out.writeShort(seed.length);
        out.write(seed);
        out.writeInt(spec.width());
        out.writeInt(spec.height());
        out.writeInt(spec.floors());
        out.writeInt(spec.geometry());
        out.writeBoolean(spec.hasFloors());
        out.writeBoolean(spec.fog());
        byte[] algorithm = spec.algorithm().getBytes(StandardCharsets.UTF_8);
        out.writeShort(algorithm.length);
        out.write(algorithm);
        out.writeInt(spec.finishMode());
        out.writeBoolean(spec.horror());
    }

    static MazeSpec readSpec(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_SEED_BYTES) throw new IOException("seed too long");
        byte[] seed = new byte[length];
        in.readFully(seed);
        int width = in.readInt(), height = in.readInt(), floors = in.readInt(), geometry = in.readInt();
        boolean hasFloors = in.readBoolean(), fog = in.readBoolean();
        int algorithmLength = in.readUnsignedShort();
        if (algorithmLength > MAX_ID_BYTES) throw new IOException("algorithm id too long");
        byte[] algorithm = new byte[algorithmLength];
        in.readFully(algorithm);
        int finishMode = in.readInt();
        boolean horror = in.readBoolean();
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) throw new IOException("bad maze size");
        if (floors < 1 || floors > MAX_FLOORS || geometry < 0) throw new IOException("bad maze shape");
        if (finishMode < 0 || finishMode >= MazeGenerator.FinishMode.values().length) throw new IOException("bad finish mode");
        return new MazeSpec(new String(seed, StandardCharsets.UTF_8), width, height, floors, geometry, hasFloors, fog,
                new String(algorithm, StandardCharsets.UTF_8), finishMode, horror);
    }
    static void writeMaze(DataOutputStream out, MazeData maze) throws IOException {
        for (int[][] floor : maze.cells()) for (int[] row : floor) for (int cell : row) out.writeByte(cell);
        for (PortalData portals : maze.portals()) {
            out.writeInt(portals.values().size());
            for (PortalData.Portal portal : portals.values()) {
                out.writeInt(portal.x); out.writeInt(portal.y); out.writeInt(portal.dirX); out.writeInt(portal.dirY);
                out.writeInt(portal.linkedX); out.writeInt(portal.linkedY);
                out.writeInt(portal.linkedDirX); out.writeInt(portal.linkedDirY); out.writeInt(portal.linkedFloor);
            }
        }
    }

    static MazeData readMaze(DataInputStream in, MazeSpec spec) throws IOException {
        int width = spec.width() % 2 == 0 ? spec.width() + 1 : spec.width();
        int height = spec.height() % 2 == 0 ? spec.height() + 1 : spec.height();
        int floors = spec.hasFloors() ? Math.max(2, spec.floors()) : 1;
        if (floors > MAX_FLOORS) throw new IOException("bad maze shape");
        int[][][] cells = new int[floors][height][width];
        for (int[][] floor : cells) {
            for (int[] row : floor) {
                for (int x = 0; x < width; x++) {
                    int cell = in.readUnsignedByte();
                    if (cell > 5) throw new IOException("bad cell value");
                    row[x] = cell;
                }
            }
        }
        PortalData[] portals = new PortalData[floors];
        for (int f = 0; f < floors; f++) {
            int count = in.readInt();
            if (count < 0 || count > width * height) throw new IOException("bad portal count");
            PortalData data = new PortalData();
            for (int i = 0; i < count; i++) {
                int x = in.readInt(), y = in.readInt(), dirX = in.readInt(), dirY = in.readInt();
                int linkedX = in.readInt(), linkedY = in.readInt(), linkedDirX = in.readInt(), linkedDirY = in.readInt();
                int linkedFloor = in.readInt();
                if (x < 0 || x >= width || y < 0 || y >= height || linkedX < 0 || linkedX >= width || linkedY < 0 || linkedY >= height
                        || linkedFloor < -1 || linkedFloor >= floors) throw new IOException("bad portal");
                PortalData.Portal portal = new PortalData.Portal(x, y, dirX, dirY);
                portal.linkedX = linkedX; portal.linkedY = linkedY; portal.linkedDirX = linkedDirX; portal.linkedDirY = linkedDirY;
                portal.linkedFloor = linkedFloor;
                data.put(portal);
            }
            portals[f] = data;
        }
        return new MazeData(Project.Helpers.SeedUtil.normalize(spec.seed()), width, height, floors, spec.hasFloors(), spec.geometry(),
                spec.algorithm(), spec.finishMode(), cells, portals);
    }
    //endregion

    //region Packets
    static byte[] state(int id, float x, float y, int floor, float angle, double finishSeconds, int serial) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TYPE_STATE);
            out.writeInt(id);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeInt(floor);
            out.writeFloat(angle);
            out.writeDouble(finishSeconds);
            out.writeInt(serial);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }

    static byte[] leave(int id) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TYPE_LEAVE);
            out.writeInt(id);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }
    static byte[] huntersPacket(List<NetSession.Hunter> hunters) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + hunters.size() * 44);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TYPE_HUNTERS);
            out.writeInt(hunters.size());
            for (NetSession.Hunter hunter : hunters) {
                out.writeInt(hunter.floor());
                out.writeFloat((float) hunter.x());
                out.writeFloat((float) hunter.y());
                out.writeByte(hunter.state());
                out.writeFloat((float) hunter.stride());
                out.writeFloat((float) hunter.alert());
                out.writeFloat((float) hunter.lean());
                out.writeFloat((float) hunter.duck());
                out.writeFloat((float) hunter.arms());
                out.writeFloat((float) hunter.scan());
                out.writeFloat((float) hunter.clock());
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }

    static byte[] rosterPacket(List<NetSession.Member> members) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + members.size() * 40);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TYPE_ROSTER);
            out.writeInt(members.size());
            for (NetSession.Member member : members) {
                out.writeInt(member.id());
                out.writeLong(member.token());
                out.writeUTF(member.address());
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }

    static byte[] caughtPacket(int id, int serial) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(10);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(TYPE_CAUGHT);
            out.writeInt(id);
            out.writeInt(serial);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }
    //endregion
}