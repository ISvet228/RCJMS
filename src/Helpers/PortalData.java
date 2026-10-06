package Helpers;

import java.util.HashMap;
import java.util.Map;

public final class PortalData {
    //region Variables
    private final Map<Long, Portal> portals = new HashMap<>();
    //endregion

    //region Public API
    public void link(int ax, int ay, int adx, int ady, int bx, int by, int bdx, int bdy) { link(ax, ay, adx, ady, false, bx, by, bdx, bdy, false); }
    public void link(int ax, int ay, int adx, int ady, boolean aOneSided, int bx, int by, int bdx, int bdy, boolean bOneSided) {
        Portal a = new Portal(ax, ay, adx, ady);
        a.linkedX = bx; a.linkedY = by; a.linkedDirX = bOneSided ? -bdx : bdx; a.linkedDirY = bOneSided ? -bdy : bdy;
        Portal b = new Portal(bx, by, bdx, bdy);
        b.linkedX = ax; b.linkedY = ay; b.linkedDirX = aOneSided ? -adx : adx; b.linkedDirY = aOneSided ? -ady : ady;
        portals.put(key(ax, ay), a);
        portals.put(key(bx, by), b);
    }
    public static void linkFloors(PortalData da, int aFloor, int ax, int ay, int adx, int ady, boolean aOneSided,
                                  PortalData db, int bFloor, int bx, int by, int bdx, int bdy, boolean bOneSided) {
        Portal a = new Portal(ax, ay, adx, ady);
        a.linkedX = bx; a.linkedY = by; a.linkedDirX = bOneSided ? -bdx : bdx; a.linkedDirY = bOneSided ? -bdy : bdy; a.linkedFloor = bFloor;
        Portal b = new Portal(bx, by, bdx, bdy);
        b.linkedX = ax; b.linkedY = ay; b.linkedDirX = aOneSided ? -adx : adx; b.linkedDirY = aOneSided ? -ady : ady; b.linkedFloor = aFloor;
        da.portals.put(key(ax, ay), a);
        db.portals.put(key(bx, by), b);
    }
    public void remove(int x, int y) { portals.remove(key(x, y)); }
    public Portal get(int x, int y) { return portals.get(key(x, y)); }
    public java.util.Collection<Portal> values() { return portals.values(); }
    public boolean isEmpty() { return portals.isEmpty(); }
    public static double[] transformPoint(Portal p, double x, double y) {
        double nax = p.dirX, nay = p.dirY, mbx = p.linkedDirX, mby = p.linkedDirY;
        double entryBaseX = p.x + Math.max(nax, 0) + Math.max(nay, 0);
        double entryBaseY = p.y + Math.max(nay, 0) + Math.max(-nax, 0);
        double exitBaseX = p.linkedX + Math.max(mbx, 0) + Math.max(mby, 0);
        double exitBaseY = p.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double px = x - entryBaseX, py = y - entryBaseY;
        return new double[]{exitBaseX + px * cosT - py * sinT, exitBaseY + px * sinT + py * cosT};
    }
    public static boolean entersFromCorrectSide(Portal p, double dirX, double dirY) { return dirX * p.dirX + dirY * p.dirY > 1e-9; }
    public static double[] transformDirection(Portal p, double dirX, double dirY) {
        double cosT = p.dirX * p.linkedDirX + p.dirY * p.linkedDirY;
        double sinT = p.dirX * p.linkedDirY - p.dirY * p.linkedDirX;
        return new double[]{dirX * cosT - dirY * sinT, dirX * sinT + dirY * cosT};
    }
    public static double[] warp(Portal p, double hitX, double hitY, double dirX, double dirY) {
        double nax = p.dirX, nay = p.dirY, mbx = p.linkedDirX, mby = p.linkedDirY;

        double u = (hitX - (p.x + Math.max(nax, 0) + Math.max(nay, 0))) * (-nay) + (hitY - (p.y + Math.max(nay, 0) + Math.max(-nax, 0))) * nax;
        u = Math.clamp(u, 0.0, 0.999999);
        double cosT = nax * mbx + nay * mby, sinT = nax * mby - nay * mbx;
        double ndx = dirX * cosT - dirY * sinT, ndy = dirX * sinT + dirY * cosT;
        double ex = (p.linkedX + Math.max(mbx, 0) + Math.max(mby, 0)) + u * (-mby), ey = (p.linkedY + Math.max(mby, 0) + Math.max(-mbx, 0)) + u * mbx;

        double eps = 1e-4;
        return new double[]{ex + ndx * eps, ey + ndy * eps, ndx, ndy};
    }
    //endregion

    //region Helpers
    private static long key(int x, int y) { return (((long) y) << 32) ^ (x & 0xffffffffL); }
    //endregion

    //region Nested Types
    public static final class Portal {
        public final int x, y, dirX, dirY;
        public int linkedX, linkedY, linkedDirX, linkedDirY, linkedFloor = -1;
        public int exitCellX() { return linkedX + linkedDirX; }
        public int exitCellY() { return linkedY + linkedDirY; }
        public Portal(int x, int y, int dirX, int dirY) { this.x = x; this.y = y; this.dirX = dirX; this.dirY = dirY; }
    }
    //endregion
}